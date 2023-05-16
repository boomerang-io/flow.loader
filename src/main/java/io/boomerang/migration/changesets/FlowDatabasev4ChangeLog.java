package io.boomerang.migration.changesets;

import static com.mongodb.client.model.Filters.eq;
import java.io.IOException;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.bson.Document;
import org.bson.conversions.Bson;
import org.bson.types.ObjectId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.StringUtils;
import com.github.cloudyrock.mongock.ChangeLog;
import com.github.cloudyrock.mongock.ChangeSet;
import com.mongodb.DuplicateKeyException;
import com.mongodb.client.FindIterable;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.IndexOptions;
import com.mongodb.client.model.Indexes;
import io.boomerang.migration.FileLoadingService;
import io.boomerang.migration.SpringContextBridge;

@ChangeLog
public class FlowDatabasev4ChangeLog {

  private static FileLoadingService fileloadingService;

  private static String collectionPrefix;

  private static boolean mongoCosmosDBTTL;

  private final Logger logger = LoggerFactory.getLogger(FlowDatabasev4ChangeLog.class);

  public FlowDatabasev4ChangeLog() {
    fileloadingService = SpringContextBridge.services().getFileLoadingService();
    collectionPrefix = SpringContextBridge.services().getCollectionPrefix();
    mongoCosmosDBTTL = SpringContextBridge.services().getMongoCosmosDBTTL();
  }

  ////////////////////////////////////////////////////////////////////////////////////
  // //
  // v4 Loader. //
  // //
  // https://github.com/boomerang-io/roadmap/issues/368 for the v3 to v4 comparison //
  // //
  ////////////////////////////////////////////////////////////////////////////////////

  /*
   * Migrates tasks_locks to task_locks to match the v4 collection naming
   * 
   * We have to use document migration to a new collection as Azure CosmosDB doesn't support
   * renaming collections
   */
  @ChangeSet(order = "4000", id = "4000", author = "Tyson Lawrie")
  public void v4MigrateTaskLockCollection(MongoDatabase db) throws IOException {
    logger.info("Commencing v4 Migration Change Sets...");
    String origCollectionName = collectionPrefix + "tasks_locks";
    String newCollectionName = collectionPrefix + "locks";

    MongoCollection<Document> origCollection = db.getCollection(origCollectionName);

    MongoCollection<Document> newCollection = db.getCollection(newCollectionName);
    if (newCollection == null) {
      db.createCollection(newCollectionName);
    }
    newCollection = db.getCollection(newCollectionName);
    if (mongoCosmosDBTTL) {
      newCollection.createIndex(Indexes.ascending("_ts"),
          new IndexOptions().expireAfter(120L, TimeUnit.SECONDS));
    } else {
      newCollection.createIndex(Indexes.ascending("expireAt"),
          new IndexOptions().expireAfter(0L, TimeUnit.MILLISECONDS));
    }

    final FindIterable<Document> locksEntities = origCollection.find();
    for (final Document lockEntity : locksEntities) {
      newCollection.insertOne(lockEntity);
    }

    origCollection.drop();
  }

  /*
   * Removes workflows_activity_task as not being migrated. THESE ARE NOT MIGRATED
   */
  @ChangeSet(order = "4001", id = "4001", author = "Tyson Lawrie")
  public void v4DropWorkflowsActivityTask(MongoDatabase db) throws IOException {
    String collectionName = collectionPrefix + "workflows_activity_task";
    db.getCollection(collectionName).drop();
  }

  /*
   * Partially migrates workflow activity so that insights and activity works at a high level.
   * 
   * Unable to migrate Workflow Activity Storage to WorkflowRun Workspaces as they werent stored in the old model
   */
  @ChangeSet(order = "4002", id = "4002", author = "Tyson Lawrie")
  public void v4MigrateWorkflowActivity(MongoDatabase db) throws IOException {
    String relationshipCollectionName = collectionPrefix + "relationships";
    MongoCollection<Document> relationshipCollection = db.getCollection(relationshipCollectionName);
    if (relationshipCollection == null) {
      db.createCollection(relationshipCollectionName);
    }
    relationshipCollection = db.getCollection(relationshipCollectionName);

    String newCollectionName = collectionPrefix + "workflow_runs";
    MongoCollection<Document> workflowRunsCollection = db.getCollection(newCollectionName);
    if (workflowRunsCollection == null) {
      db.createCollection(newCollectionName);
    }
    workflowRunsCollection = db.getCollection(newCollectionName);

    String collectionName = collectionPrefix + "workflows_activity";
    MongoCollection<Document> workflowsActivityCollection = db.getCollection(collectionName);

    final FindIterable<Document> workflowsActivityEntities = workflowsActivityCollection.find();
    for (final Document workflowsActivityEntity : workflowsActivityEntities) {
      List<Document> labels = (List<Document>) workflowsActivityEntity.get("labels");
      Map<String, String> newLabels = new HashMap<>();
      if (labels != null && !labels.isEmpty())
        for (final Document label : labels) {
          newLabels.put(label.getString("key"), label.getString("value"));
        }
      workflowsActivityEntity.replace("labels", newLabels);
      Map<String, Object> annotations = new HashMap<>();
      annotations.put("io#boomerang/generation", "3");
      annotations.put("io#boomerang/kind", "WorkflowRun");
      workflowsActivityEntity.put("annotations", annotations);

      // Migrate initiated by
      String initiatedByRef = "";
      if (workflowsActivityEntity.get("initiatedByUserId") != null) {
        initiatedByRef = (String) workflowsActivityEntity.get("initiatedByUserId");
      } else {
        initiatedByRef = (String) workflowsActivityEntity.get("initiatedByUserName");
      }
      workflowsActivityEntity.remove("initiatedByUserId");
      workflowsActivityEntity.remove("initiatedByUserName");

      Long duration = (Long) workflowsActivityEntity.get("duration");
      long newDuration = 0;
      if (duration != null) {
        newDuration = duration;
      }
      workflowsActivityEntity.replace("duration", newDuration);
      workflowsActivityEntity.put("startTime", workflowsActivityEntity.get("creationDate"));

      String status = (String) workflowsActivityEntity.get("status");
      if (status == null) {
        status = "failed";
        workflowsActivityEntity.put("status", "failed");
      }
      switch (status) {
        case "inProgress":
          workflowsActivityEntity.put("status", "running");
          break;
        case "completed":
          workflowsActivityEntity.put("status", "succeeded");
          break;
        case "failure":
          workflowsActivityEntity.put("status", "failed");
          break;
        default:
      }
      workflowsActivityEntity.put("phase", "finalized");

      String statusOverride = (String) workflowsActivityEntity.get("statusOverride");
      if (statusOverride != null) {
        if ("completed".equals(statusOverride)) {
          statusOverride = "succeeded";
        } else if ("failure".equals(statusOverride)) {
          statusOverride = "failed";
        }
        workflowsActivityEntity.replace("statusOverride", statusOverride);
      }

      // Change from ID to Ref based linkage
      workflowsActivityEntity.put("workflowRef", workflowsActivityEntity.get("workflowId"));
      workflowsActivityEntity.remove("workflowId");
      workflowsActivityEntity.put("workflowRevisionRef",
          workflowsActivityEntity.get("workflowRevisionid"));
      workflowsActivityEntity.remove("workflowRevisionid");

      // Convert properties to params
      List<Document> properties = (List<Document>) workflowsActivityEntity.get("properties");
      List<Document> params = new LinkedList<>();
      if (properties != null && !properties.isEmpty()) {
        for (final Document property : properties) {
          Document param = new Document();
          param.put("name", property.get("key"));
          param.put("value", property.get("value"));
          params.add(param);
        }
      }
      workflowsActivityEntity.put("params", params);
      workflowsActivityEntity.remove("properties");
      
      // Convert outputProperties to Results
      List<Document> outputProperties = (List<Document>) workflowsActivityEntity.get("outputProperties");
      List<Document> results = new LinkedList<>();
      if (outputProperties != null && !outputProperties.isEmpty()) {
        for (final Document outputProperty : outputProperties) {
          Document result = new Document();
          result.put("name", outputProperty.get("key"));
          result.put("value", outputProperty.get("value"));
          results.add(result);
        }
      }
      workflowsActivityEntity.put("results", results);
      workflowsActivityEntity.remove("outputProperties");

      workflowsActivityEntity.remove("switchValue");

      // Convert owner to relationship
      Document relationship = new Document();
      relationship.put("relationship", "belongs-to");
      relationship.put("fromType", "WorkflowRun");
      relationship.put("fromRef", workflowsActivityEntity.get("_id").toString());
      if ("user".equals((String) workflowsActivityEntity.get("scope"))) {
        relationship.put("toType", "User");
        relationship.put("toRef", workflowsActivityEntity.get("userId"));
      } else if ("team".equals((String) workflowsActivityEntity.get("scope"))) {
        relationship.put("toType", "Team");
        relationship.put("toRef", workflowsActivityEntity.get("teamId"));
      } else {
        relationship.put("toType", StringUtils.capitalize((String) workflowsActivityEntity.get("scope")));
      }
      relationshipCollection.insertOne(relationship);
      workflowsActivityEntity.remove("teamId");
      workflowsActivityEntity.remove("userId");
      workflowsActivityEntity.remove("scope");

      workflowRunsCollection.insertOne(workflowsActivityEntity);
    }

    workflowsActivityCollection.drop();
  }

  /*
   * Migrates workflow activity approvals to workflow actions.
   * 
   */
  @ChangeSet(order = "4003", id = "4003", author = "Tyson Lawrie")
  public void v4MigrateWorkflowActions(MongoDatabase db) throws IOException {
    String newCollectionName = collectionPrefix + "actions";
    MongoCollection<Document> workflowActionsCollection = db.getCollection(newCollectionName);
    if (workflowActionsCollection == null) {
      db.createCollection(newCollectionName);
    }
    workflowActionsCollection = db.getCollection(newCollectionName);

    String collectionName = collectionPrefix + "workflows_activity_approval";
    MongoCollection<Document> workflowsActivityApprovalCollection =
        db.getCollection(collectionName);

    final FindIterable<Document> workflowsActivityApprovalEntities =
        workflowsActivityApprovalCollection.find();
    for (final Document workflowsActivityApprovalEntity : workflowsActivityApprovalEntities) {
      workflowsActivityApprovalEntity.put("workflowRef",
          workflowsActivityApprovalEntity.get("workflowId"));
      workflowsActivityApprovalEntity.remove("workflowId");
      workflowsActivityApprovalEntity.put("workflowRunRef",
          workflowsActivityApprovalEntity.get("activityId"));
      workflowsActivityApprovalEntity.remove("activityId");
      workflowsActivityApprovalEntity.put("taskRunRef",
          workflowsActivityApprovalEntity.get("taskActivityId"));
      workflowsActivityApprovalEntity.remove("taskActivityId");
      String type = (String) workflowsActivityApprovalEntity.get("type");
      if ("task".equals(type)) {
        workflowsActivityApprovalEntity.replace("type", "manual");
      }

      workflowsActivityApprovalEntity.remove("teamId");
      workflowActionsCollection.insertOne(workflowsActivityApprovalEntity);
    }

    workflowsActivityApprovalCollection.drop();
  }

  /*
   * Task Templates migration required for Flow v4
   */
  @ChangeSet(order = "4004", id = "4004", author = "Tyson Lawrie")
  public void v4MigrationTaskTemplates(MongoDatabase db) throws IOException {
    String relationshipCollectionName = collectionPrefix + "relationships";
    MongoCollection<Document> relationshipCollection = db.getCollection(relationshipCollectionName);
    if (relationshipCollection == null) {
      db.createCollection(relationshipCollectionName);
    }
    relationshipCollection = db.getCollection(relationshipCollectionName);

    MongoCollection<Document> taskTemplatesCollection =
        db.getCollection(collectionPrefix + "task_templates");

    final FindIterable<Document> taskTemplateEntities = taskTemplatesCollection.find();
    for (final Document taskTemplateEntity : taskTemplateEntities) {
      Document newTaskTemplateEntity = new Document();
      newTaskTemplateEntity.put("name",
          taskTemplateEntity.get("name").toString().toLowerCase().replace(' ', '-'));
      newTaskTemplateEntity.put("displayName", taskTemplateEntity.get("name"));
      newTaskTemplateEntity.put("status", taskTemplateEntity.get("status"));
      newTaskTemplateEntity.put("description", taskTemplateEntity.get("description"));
      newTaskTemplateEntity.put("category", taskTemplateEntity.get("category"));
      newTaskTemplateEntity.put("icon", taskTemplateEntity.get("icon"));
      newTaskTemplateEntity.put("verified", taskTemplateEntity.get("verified"));
      if (taskTemplateEntity.get("scope") != null) {
        newTaskTemplateEntity.put("scope", (String) taskTemplateEntity.get("scope"));
      } else {
        newTaskTemplateEntity.put("scope", "global");
      }
      Map<String, String> labels = new HashMap<>();
      newTaskTemplateEntity.put("labels", labels);
      Map<String, Object> annotations = new HashMap<>();
      annotations.put("io#boomerang/generation", "3");
      annotations.put("io#boomerang/kind", "TaskTemplate");
      newTaskTemplateEntity.put("annotations", annotations);

      newTaskTemplateEntity.put("creationDate", taskTemplateEntity.get("createdDate"));

      if ("templateTask".equals(taskTemplateEntity.get("nodetype"))) {
        newTaskTemplateEntity.put("type", "template");
      } else if ("customTask".equals(taskTemplateEntity.get("nodetype"))) {
        newTaskTemplateEntity.put("type", "custom");
      } else {
        newTaskTemplateEntity.put("type", taskTemplateEntity.get("nodetype"));
      }

      List<Document> revisions = (List<Document>) taskTemplateEntity.get("revisions");
      if (revisions != null && !revisions.isEmpty()) {
        for (final Document revision : revisions) {
          newTaskTemplateEntity.put("version", revision.get("version"));
          newTaskTemplateEntity.put("changelog", revision.get("changelog"));
          List<Document> configs = (List<Document>) revision.get("config");
          newTaskTemplateEntity.put("config", configs);
          List<Document> params = new LinkedList<>();
          if (!configs.isEmpty()) {
            for (final Document config : configs) {
              Document param = new Document();
              param.put("name", config.get("key"));
              param.put("type", "string");
              param.put("description", config.get("description"));
              param.put("defaultValue", config.get("defaultValue"));
              params.add(param);
            }
          }
          Document spec = new Document();
          spec.put("params", params);
          spec.put("arguments", revision.get("arguments"));
          spec.put("command", revision.get("command"));
          spec.put("envs", revision.get("envs"));
          spec.put("image", revision.get("image"));
          spec.put("results", revision.get("results"));
          spec.put("script", revision.get("script"));
          spec.put("workingDir", revision.get("workingDir"));
          spec.put("script", revision.get("script"));
          newTaskTemplateEntity.put("spec", spec);

          // Convert owner to relationship
          Document relationship = new Document();
          relationship.put("relationship", "belongs-to");
          relationship.put("fromType", "TaskTemplate");
          if ("team".equals((String) newTaskTemplateEntity.get("scope"))) {
            relationship.put("toType", "Team");
            relationship.put("toRef", taskTemplateEntity.get("flowTeamId"));
          } else {
            relationship.put("toType", StringUtils.capitalize((String) newTaskTemplateEntity.get("scope")));
          }

          if (revision.get("version").equals(1)) {
            newTaskTemplateEntity.put("_id", taskTemplateEntity.get("_id"));
            taskTemplatesCollection.replaceOne(eq("_id", taskTemplateEntity.getObjectId("_id")),
                newTaskTemplateEntity);
            relationship.put("fromRef", taskTemplateEntity.getObjectId("_id").toString());
          } else {
            ObjectId newId = new ObjectId();
            newTaskTemplateEntity.put("_id", newId);
            taskTemplatesCollection.insertOne(newTaskTemplateEntity);
            relationship.put("fromRef", newId.toString());
          }

          // Store relationship
          // Needs to sleep as the ObjectID is created using Date in Seconds which unfortunately can
          // cause non unique keys to be generated. Hence we sleep for a second.
          try {
            relationshipCollection.insertOne(relationship);
          } catch (DuplicateKeyException dke) {
            try {
              Thread.sleep(1000);
            } catch (InterruptedException e) {
              e.printStackTrace();
              Thread.currentThread().interrupt();
            }
            relationshipCollection.insertOne(relationship);
          }
        }
      }
    }
  }

  /*
   * Migrates workflows and workflows_revisions to v4 collections and structure Extremely complex
   * migration!
   * 
   */
  @ChangeSet(order = "4005", id = "4005", author = "Tyson Lawrie")
  public void v4MigrateWorkflowsAndRevisions(MongoDatabase db) throws IOException {
    String relationshipCollectionName = collectionPrefix + "relationships";
    MongoCollection<Document> relationshipCollection = db.getCollection(relationshipCollectionName);
    if (relationshipCollection == null) {
      db.createCollection(relationshipCollectionName);
    }
    relationshipCollection = db.getCollection(relationshipCollectionName);

    String taskTemplateCollectionName = collectionPrefix + "task_templates";
    MongoCollection<Document> taskTemplatesCollection =
        db.getCollection(taskTemplateCollectionName);

    String newRevisionCollectionName = collectionPrefix + "workflow_revisions";
    MongoCollection<Document> workflowRevisionsCollection =
        db.getCollection(newRevisionCollectionName);
    if (workflowRevisionsCollection == null) {
      db.createCollection(newRevisionCollectionName);
    }
    workflowRevisionsCollection = db.getCollection(newRevisionCollectionName);

    String revisionCollectionName = collectionPrefix + "workflows_revisions";
    MongoCollection<Document> workflowsRevisionsCollection =
        db.getCollection(revisionCollectionName);
    final FindIterable<Document> workflowsRevisionEntities = workflowsRevisionsCollection.find();

    String workflowsCollectionName = collectionPrefix + "workflows";
    MongoCollection<Document> workflowsCollection = db.getCollection(workflowsCollectionName);

    final FindIterable<Document> workflowsEntities = workflowsCollection.find();
    for (final Document workflowsEntity : workflowsEntities) {
      logger.info("Migrating WorkflowId: " + workflowsEntity.get("_id"));

      // Convert Labels
      List<Document> labels = (List<Document>) workflowsEntity.get("labels");
      Map<String, String> newLabels = new HashMap<>();
      if (labels != null) {
        for (final Document label : labels) {
          newLabels.put(label.getString("key"), label.getString("value"));
        }
        workflowsEntity.replace("labels", newLabels);
      } else {
        workflowsEntity.put("labels", newLabels);
      }

      // Set an annotation that this Workflow existing prior to v4
      Map<String, Object> annotations = new HashMap<>();
      annotations.put("io#boomerang/generation", "3");
      annotations.put("io#boomerang/kind", "Workflow");
      workflowsEntity.put("annotations", annotations);

      // Storage to Workspaces conversion. Only added if enabled.
      List<Document> workspaces = new LinkedList<>();
      if (workflowsEntity.containsKey("storage")) {
        Document storage = (Document) workflowsEntity.get("storage");
        Document activityStorage = (Document) storage.get("activity");
        if (activityStorage.getBoolean("enabled", false)) {
          Document wfRunWorkspace = new Document();
          wfRunWorkspace.put("name", "workflowrun");
          wfRunWorkspace.put("type", "workflowrun");
          wfRunWorkspace.put("optional", false);
          activityStorage.remove("enabled");
          wfRunWorkspace.put("spec", activityStorage);
          workspaces.add(wfRunWorkspace);
        }
        Document workflowStorage = (Document) storage.get("workflow");
        if (workflowStorage.getBoolean("enabled", false)) {
          Document workflowWorkspace = new Document();
          workflowWorkspace.put("name", "workflow");
          workflowWorkspace.put("type", "workflow");
          workflowWorkspace.put("optional", false);
          workflowStorage.remove("enabled");
          workflowWorkspace.put("spec", workflowStorage);
          workspaces.add(workflowWorkspace);
        }
        workflowsEntity.remove("storage");
      }

      // Migrate Properties to Config and Parameters
      List<Document> properties = (List<Document>) workflowsEntity.get("properties");
      List<Document> params = new LinkedList<>();
      if (!properties.isEmpty()) {
        for (final Document property : properties) {
          Document param = new Document();
          param.put("name", property.get("key"));
          param.put("type", "string");
          param.put("description", property.get("description"));
          param.put("defaultValue", property.get("defaultValue"));
          params.add(param);
        }
      }
      workflowsEntity.remove("properties");

      // Migrate the Revisions
      // Need to migrate the dag and config first as config means a different thing post migration
      for (Document workflowRevisionEntity : workflowsRevisionEntities) {
        if (!workflowRevisionEntity.get("workFlowId")
            .equals(workflowsEntity.get("_id").toString())) {
          continue;
        }
        if (Long.valueOf(1).equals(workflowRevisionEntity.get("version"))) {
          // Set Creation Date from first revisions changelog
          Document firstRevisionChangelog = (Document) workflowRevisionEntity.get("changelog");
          workflowsEntity.put("creationDate", firstRevisionChangelog.get("date"));
        }
        logger.info("Migrating Workflow Revision: " + workflowRevisionEntity.get("_id"));
        Document dag = (Document) workflowRevisionEntity.get("dag");
        workflowRevisionEntity.put("workflowRef", workflowRevisionEntity.get("workFlowId"));
        workflowRevisionEntity.remove("workFlowId");
        workflowRevisionEntity.replace("version",
            (Integer) workflowRevisionEntity.getLong("version").intValue());

        List<Document> dagTasks = (List<Document>) dag.get("tasks");
        List<Document> dagTasksRef = (List<Document>) dag.get("tasks");
        Document config = (Document) workflowRevisionEntity.get("config");
        List<Document> configNodes = (List<Document>) dag.get("nodes");
        List<Document> tasks = new LinkedList<>();
        for (final Document dagTask : dagTasks) {
          Document task = new Document();
          Map<String, String> taskLabels = new HashMap<>();
          Map<String, Object> taskAnnotations = new HashMap<>();

          if (dagTask.getString("type").equals("start")) {
            task.put("name", "start");
          } else if (dagTask.getString("type").equals("end")) {
            task.put("name", "end");
          } else {
            task.put("name", dagTask.getString("label"));

            // Set Template Ref - need to find task template name
            Document taskTemplateEntity = taskTemplatesCollection
                .find(eq("_id", new ObjectId(dagTask.get("templateId").toString()))).first();
            task.put("templateRef", taskTemplateEntity.getString("name"));
            task.replace("templateVersion", (Integer) dagTask.get("templateVersion"));
            task.remove("templateId");

            // Migrate Results - no change
            task.put("results", dagTask.get("results"));

            // Migrate Properties to Params
            List<Document> dagProperties = (List<Document>) dagTask.get("properties");
            List<Document> taskParams = new LinkedList<>();
            if (dagProperties != null) {
              for (final Document dagProperty : dagProperties) {
                Document param = new Document();
                param.put("name", dagProperty.get("key"));
                param.put("value", dagProperty.get("value"));
                taskParams.add(param);
              }
            }
            task.put("params", taskParams);
            task.remove("properties");
          }

          // Migrate Type - no change
          task.put("type", dagTask.getString("type"));

          // Migrate Dependencies
          List<Document> dependencies = (List<Document>) dagTask.get("dependencies");
          if (dependencies != null) {
            for (final Document dependency : dependencies) {
              // TODO: confirm if we need the points metadata
              // Document dependencyMetadata = (Document) dependency.get("metadata");
              // if (dependencyMetadata != null) {
              // taskAnnotations.put("io#boomerang/points", dependencyMetadata.get("points"));
              // }
              dependency.put("decisionCondition",
                  dependency.get("switchCondition") != null ? dependency.get("switchCondition")
                      : "");
              Document dependentTask = dagTasksRef.stream()
                  .filter(e -> e.get("taskId").equals(dependency.get("taskId"))).findFirst().get();
              logger.info("Dependent Task: " + dependentTask.get("label"));
              dependency.put("taskRef", dependentTask.get("label"));
              dependency.remove("taskId");
              dependency.remove("switchCondition");
              dependency.remove("conditionalExecution");
              dependency.remove("additionalProperties");
              // dependency.remove("metadata");
            }
          }
          task.put("dependencies", dependencies);

          // Migrate Position Metadata
          Document metadata = (Document) dagTask.get("metadata");
          if (metadata.get("position") != null) {
            taskAnnotations.put("io#boomerang/position", metadata.get("position"));
          }

          task.put("labels", taskLabels);
          task.put("annotations", taskAnnotations);
          tasks.add(task);
        }
        workflowRevisionEntity.remove("dag");
        workflowRevisionEntity.remove("config");

        workflowRevisionEntity.put("tasks", tasks);
        workflowRevisionEntity.put("workspaces", workspaces);
        workflowRevisionEntity.put("config", properties);
        workflowRevisionEntity.put("params", params);

        workflowRevisionsCollection.insertOne(workflowRevisionEntity);
      }

      // Convert owner to relationship
      Document relationship = new Document();
      relationship.put("relationship", "belongs-to");
      relationship.put("fromType", "Workflow");
      relationship.put("fromRef", workflowsEntity.getObjectId("_id").toString());
      if ("user".equals((String) workflowsEntity.get("scope"))) {
        relationship.put("toType", "User");
        relationship.put("toRef", workflowsEntity.get("ownerUserId"));
      } else if ("team".equals((String) workflowsEntity.get("scope"))) {
        relationship.put("toType", "Team");
        relationship.put("toRef", workflowsEntity.get("flowTeamId"));
      } else {
        relationship.put("toType", StringUtils.capitalize((String) workflowsEntity.get("scope")));
      }
      relationshipCollection.insertOne(relationship);
      workflowsEntity.remove("flowTeamId");
      workflowsEntity.remove("ownerUserId");

      workflowsCollection.replaceOne(eq("_id", workflowsEntity.getObjectId("_id")),
          workflowsEntity);
    }

     workflowsRevisionsCollection.drop();
  }

  /*
   * MongoDB supports sorting without indexes, it does recommend them. Cosmos needs them and can't
   * automatic sort without them.
   * 
   * Ref: https://www.mongodb.com/docs/manual/tutorial/sort-results-with-indexes/ Ref:
   * https://mongodb.github.io/mongo-java-driver/3.5/driver/tutorials/indexes/#compound-indexes Ref:
   * https://learn.microsoft.com/en-us/azure/cosmos-db/index-policy#order-by-queries-on-multiple-
   * properties
   */
  @ChangeSet(order = "4006", id = "4006", author = "Tyson Lawrie")
  public void v4CreateSortIndexes(MongoDatabase db) throws IOException {
    String workflowsCollectionName = collectionPrefix + "workflows";
    MongoCollection<Document> workflowsCollection = db.getCollection(workflowsCollectionName);
    workflowsCollection.createIndex(Indexes.descending("creationDate"));

    String workflowRevisionsCollectionName = collectionPrefix + "workflow_revisions";
    MongoCollection<Document> workflowRevisionsCollection =
        db.getCollection(workflowRevisionsCollectionName);
    workflowRevisionsCollection.createIndex(Indexes.descending("version"));
    workflowRevisionsCollection.createIndex(
        Indexes.compoundIndex(Indexes.descending("workflowRef"), Indexes.descending("version")));

    String taskTemplatesCollectionName = collectionPrefix + "task_templates";
    MongoCollection<Document> taskTemplatesCollection =
        db.getCollection(taskTemplatesCollectionName);
    taskTemplatesCollection.createIndex(Indexes.descending("version"));
    taskTemplatesCollection.createIndex(Indexes.descending("creationDate"));

    String taskRunsCollectionName = collectionPrefix + "task_runs";
    MongoCollection<Document> taskRunsCollection = db.getCollection(taskRunsCollectionName);
    taskRunsCollection.createIndex(Indexes.descending("creationDate"));

    String workflowRunsCollectionName = collectionPrefix + "workflow_runs";
    MongoCollection<Document> workflowRunsCollection = db.getCollection(workflowRunsCollectionName);
    workflowRunsCollection.createIndex(Indexes.descending("creationDate"));
  }

  /*
   * Creates the Relationship collection.
   * 
   * While the prior v4 loaders do reference this collection, it was introduced after the loader had
   * been used by the community Hence we do safe check creation.
   */
  @ChangeSet(order = "4007", id = "4007", author = "Tyson Lawrie")
  public void v4CreateRelationshipCollection(MongoDatabase db) throws IOException {
    String relationshipCollectionName = collectionPrefix + "relationships";
    MongoCollection<Document> relationshipCollection = db.getCollection(relationshipCollectionName);
    if (relationshipCollection == null) {
      db.createCollection(relationshipCollectionName);
    }
    relationshipCollection = db.getCollection(relationshipCollectionName);
    relationshipCollection.createIndex(Indexes.descending("fromRef"));
    relationshipCollection.createIndex(Indexes.descending("toRef"));
  }

  /*
   * Creates additional indexes for the TaskRun & WorkflowRun Query.
   * 
   * While the prior v4 loaders do reference this collection, it was introduced after the loader had
   * been used by the community Hence we do safe check creation.
   */
  @ChangeSet(order = "4008", id = "4008", author = "Tyson Lawrie")
  public void v4CreateQueryIndexes(MongoDatabase db) throws IOException {
    String taskRunsCollectionName = collectionPrefix + "task_runs";
    MongoCollection<Document> taskRunsCollection = db.getCollection(taskRunsCollectionName);
    taskRunsCollection.createIndex(Indexes.ascending("labels.$**"));
    taskRunsCollection.createIndex(Indexes.descending("status"));
    taskRunsCollection.createIndex(Indexes.descending("phase"));
    
    String workflowRunsCollectionName = collectionPrefix + "workflow_runs";
    MongoCollection<Document> workflowRunsCollection = db.getCollection(workflowRunsCollectionName);
    workflowRunsCollection.createIndex(Indexes.ascending("labels.$**"));
    workflowRunsCollection.createIndex(Indexes.descending("status"));
    workflowRunsCollection.createIndex(Indexes.descending("phase"));
  }

  /*
   * Creates additional indexes for the TaskRun & WorkflowRun Query.
   * 
   * While the prior v4 loaders do reference this collection, it was introduced after the loader had
   * been used by the community Hence we do safe check creation.
   */
  @ChangeSet(order = "4009", id = "4009", author = "Tyson Lawrie")
  public void v4CreateQueryIndexes2(MongoDatabase db) throws IOException {
    String taskRunsCollectionName = collectionPrefix + "task_runs";
    MongoCollection<Document> taskRunsCollection = db.getCollection(taskRunsCollectionName);
    taskRunsCollection.createIndex(Indexes.descending("name"));
    taskRunsCollection.createIndex(Indexes.descending("workflowRunRef"));
    
    String taskTemplatesCollectionName = collectionPrefix + "task_templates";
    MongoCollection<Document> taskTemplatesCollection = db.getCollection(taskTemplatesCollectionName);
    taskTemplatesCollection.createIndex(Indexes.descending("name"));
  }
  
  /*
   * Load in v2 of the Sleep TaskTemplate migrating to a system task
   */
  @ChangeSet(order = "4010", id = "4010", author = "Tyson Lawrie")
  public void updateSleepTaskTemplate(MongoDatabase db) throws IOException {
    final List<String> files = fileloadingService.loadFiles("flow/4010/task_templates/*.json");
    for (final String fileContents : files) {
      final Document doc = Document.parse(fileContents);
      final MongoCollection<Document> collection =
          db.getCollection(collectionPrefix + "task_templates");
      collection.insertOne(doc);
    }
  }

  /*
   * Migrate Teams
   * 
   * Need to check if Teams collection exists as for IBM Services Essentials it may not with the change to internalised teams.
   * 
   */
  @ChangeSet(order = "4011", id = "4011", author = "Tyson Lawrie")
  public void v4MigrateTeams(MongoDatabase db) throws IOException {
    String relationshipCollectionName = collectionPrefix + "relationships";
    MongoCollection<Document> relationshipCollection = db.getCollection(relationshipCollectionName);

    String teamsCollectionName = collectionPrefix + "teams";
    MongoCollection<Document> teamsCollection =
        db.getCollection(teamsCollectionName);
    if (teamsCollection == null) {
      db.createCollection(teamsCollectionName);
    }
    teamsCollection = db.getCollection(teamsCollectionName);

    final FindIterable<Document> teamsEntities = teamsCollection.find();
    for (final Document teamsEntity : teamsEntities) {
      logger.info("Migrating Team - ID: " + teamsEntity.get("_id"));

      teamsEntity.put("creationDate", new Date());
      
      // Convert Labels
      List<Document> labels = (List<Document>) teamsEntity.get("labels");
      Map<String, String> newLabels = new HashMap<>();
      if (labels != null) {
        for (final Document label : labels) {
          newLabels.put(label.getString("key"), label.getString("value"));
        }
        teamsEntity.replace("labels", newLabels);
      } else {
        teamsEntity.put("labels", newLabels);
      }

      // Migrate HLG ID to externalRef
      teamsEntity.put("externalRef",
          teamsEntity.get("higherLevelGroupId"));
      teamsEntity.remove("higherLevelGroupId");
      
      //Migrate Status
      Boolean isActive = (Boolean) teamsEntity.get("isActive");
      if (isActive) {
        teamsEntity.put("status", "active");
      } else {
        teamsEntity.put("status", "inactive");
      }
      teamsEntity.remove("isActive");
      
      // Migrate Properties to Parameters and bump into higher level
      Document settings = (Document) teamsEntity.get("settings");
      if (settings != null) {
        teamsEntity.put("parameters",
            settings.get("properties") != null ? settings.get("properties")
                : new LinkedList<>());
      } else {
        teamsEntity.put("parameters", new LinkedList<>());
      }
      teamsEntity.remove("settings");
      
      //Migrate ApproverGroups
      List<Document> approverGroups = (List<Document>) teamsEntity.get("approverGroups");
      if (approverGroups != null) {
        for (final Document approverGroup : approverGroups) {
          approverGroup.put("creationDate", new Date());
          List<Document> approvers = (List<Document>) approverGroup.get("approvers");
          List<String> approverRefs = new LinkedList<>();
          if (approvers != null) {
            for (final Document approver : approvers) {
              approverRefs.add(approver.get("userId").toString());
            }
          }
          approverGroup.put("approverRefs", approverRefs);
          approverGroup.remove("approvers");
          ObjectId newId = new ObjectId();
          approverGroup.put("_id", newId);
          
          //Add relationship between Team and ApproverGroup
          Document relationship = new Document();
          relationship.put("type", "BELONGSTO");
          relationship.put("from", "APPROVERGROUP");
          relationship.put("fromRef", newId);
          relationship.put("to", "TEAM");
          relationship.put("toRef", teamsEntity.get("_id"));
          relationshipCollection.insertOne(relationship);
        }
      }
      teamsEntity.remove("approverGroups");
      teamsCollection.replaceOne(eq("_id", teamsEntity.getObjectId("_id")),
          teamsEntity);
    }
  }

  /*
   * Migrate Relationships in case prior loaders were run in early Beta versions
   * 
   * - Add creationDate
   * - Change relationship string
   * 
   */
  @ChangeSet(order = "4012", id = "4012", author = "Tyson Lawrie")
  public void v4MigrateRelationships(MongoDatabase db) throws IOException {
    String relationshipCollectionName = collectionPrefix + "relationships";
    MongoCollection<Document> relationshipCollection = db.getCollection(relationshipCollectionName);
    final FindIterable<Document> relationshipEntities = relationshipCollection.find();
    for (final Document relationshipEntity : relationshipEntities) {
      logger.info("Migrating Relationship - ID: " + relationshipEntity.get("_id"));

      if (relationshipEntity.get("creationDate") == null) {
        relationshipEntity.put("creationDate", new Date());
      }
      if ("belongs-to".equals(relationshipEntity.get("relationship").toString())) {
        relationshipEntity.put("type", "BELONGSTO");
        relationshipEntity.remove("relationship");
      }
      if (relationshipEntity.get("fromType") != null) {
        relationshipEntity.put("from", relationshipEntity.get("fromType"));
        relationshipEntity.remove("fromType");
      }
      relationshipEntity.put("from", relationshipEntity.get("from").toString().toUpperCase());
      if (relationshipEntity.get("toType") != null) {
        relationshipEntity.put("to", relationshipEntity.get("toType"));
        relationshipEntity.remove("toType");
      }
      relationshipEntity.put("to", relationshipEntity.get("to").toString().toUpperCase());

      relationshipCollection.replaceOne(eq("_id", relationshipEntity.getObjectId("_id")),
          relationshipEntity);
    }

    // Update Indexes
    relationshipCollection.dropIndexes();
    relationshipCollection.createIndex(Indexes.descending("toRef"));
    relationshipCollection.createIndex(Indexes.descending("type"));
    relationshipCollection.createIndex(Indexes.descending("from"));
    relationshipCollection.createIndex(Indexes.descending("to"));
    relationshipCollection.createIndex(Indexes.descending("fromType"));
    relationshipCollection.createIndex(Indexes.descending("toType"));
  }

  /*
   * Migrate Changelogs
   * 
   * - Remove userName (PI)
   * - Rename userId to Author
   * 
   */
  @ChangeSet(order = "4013", id = "4013", author = "Tyson Lawrie")
  public void v4MigrateChangelog(MongoDatabase db) throws IOException {
    logger.info("Migrating Workflow Revision Changelogs");
    String revisionCollectionName = collectionPrefix + "workflow_revisions";
    MongoCollection<Document> workflowRevisionsCollection =
        db.getCollection(revisionCollectionName);
    
    final FindIterable<Document> workflowRevisionEntities = workflowRevisionsCollection.find();
    for (final Document workflowRevisionEntity : workflowRevisionEntities) {
      if (workflowRevisionEntity.get("changelog") != null) {
        Document changelog = (Document) workflowRevisionEntity.get("changelog");
        if (changelog != null) {
          if (changelog.get("userId") != null) {
            changelog.put("author", changelog.get("userId"));
            changelog.remove("userId");
          }
          changelog.remove("userName");
          workflowRevisionEntity.replace("changelog", changelog);
          workflowRevisionsCollection.replaceOne(eq("_id", workflowRevisionEntity.getObjectId("_id")),
              workflowRevisionEntity);
        }
      }
    }
    
    MongoCollection<Document> taskTemplatesCollection =
        db.getCollection(collectionPrefix + "task_templates");

    logger.info("Migrating TaskTemplate Changelogs");
    final FindIterable<Document> taskTemplateEntities = taskTemplatesCollection.find();
    for (final Document taskTemplateEntity : taskTemplateEntities) {
        Document changelog = (Document) taskTemplateEntity.get("changelog");
        if (changelog != null) {
          if (changelog.get("userId") != null) {
            changelog.put("author", changelog.get("userId"));
            changelog.remove("userId");
          }
          changelog.remove("userName");
          taskTemplateEntity.replace("changelog", changelog);
          taskTemplatesCollection.replaceOne(eq("_id", taskTemplateEntity.getObjectId("_id")),
              taskTemplateEntity);
        }
    }
  }

  /*
   * Migrate Users
   * 
   * - Each User needs to have a uniquely named team
   * - Migrate users teams list to Relationships
   * - Any previous Workflow Relationship to a User, needs to be to the new Team
   * 
   */
  @ChangeSet(order = "4014", id = "4014", author = "Tyson Lawrie")
  public void v4MigrateUsersToTeam(MongoDatabase db) throws IOException {
    String usersCollectionName = collectionPrefix + "users";
    MongoCollection<Document> usersCollection = db.getCollection(usersCollectionName);
    
    String teamsCollectionName = collectionPrefix + "teams";
    MongoCollection<Document> teamsCollection = db.getCollection(teamsCollectionName);
    
    String relationshipsCollectionName = collectionPrefix + "relationships";
    MongoCollection<Document> relationshipsCollection = db.getCollection(relationshipsCollectionName);
    
    final FindIterable<Document> userEntities = usersCollection.find();
    for (final Document userEntity : userEntities) {
      logger.info("Migrating Users - ID: " + userEntity.get("_id").toString());
      String userName = userEntity.getString("name");
      Document team = new Document();
      if (userEntity.get("quotas") != null) {
        Document quotas = (Document) userEntity.get("quotas");
        userEntity.remove("quotes");
        team.put("quotas", quotas);
      }
      String teamName =  userName.replace("@", "-").replace(".", "-") + " Personal Team";
      team.put("name", teamName);
      if (userEntity.get("status") != null && "active".equals(userEntity.get("status"))) {
        team.put("status", "active");
      } else {
        team.put("status", "inactive");
      }
      team.put("creationDate", new Date());

      ObjectId newTeamId = new ObjectId();
      team.put("_id", newTeamId);
      teamsCollection.insertOne(team);
      Document newTeamRelationship = new Document();
      newTeamRelationship.put("type", "MEMBEROF");
      newTeamRelationship.put("from", "USER");
      newTeamRelationship.put("fromRef", userEntity.get("_id").toString());
      newTeamRelationship.put("to", "TEAM");
      newTeamRelationship.put("toRef", newTeamId.toString());
      relationshipsCollection.insertOne(newTeamRelationship);
      
      // If User was a member of existing Teams, add Relationship for those too
      if (userEntity.get("flowTeams") != null) {
        List<String> teamsList = (List<String>) userEntity.get("flowTeams");
        for (String teamId : teamsList) {
          Document relationship = new Document();
          relationship.put("type", "MEMBEROF");
          relationship.put("from", "USER");
          relationship.put("fromRef", userEntity.get("_id").toString());
          relationship.put("to", "TEAM");
          relationship.put("toRef", teamId.toString());
          relationshipsCollection.insertOne(relationship);
        }
      }
      userEntity.remove("flowTeams");
      userEntity.remove("quotas");
      userEntity.put("creationDate", userEntity.get("firstLoginDate"));
      userEntity.remove("firstLoginDate");
      Document settings = new Document();
      settings.put("isFirstVisit", userEntity.get("isFirstVisit"));
      userEntity.remove("isFirstVisit");
      settings.put("hasConsented", userEntity.get("hasConsented"));
      userEntity.remove("hasConsented");
      userEntity.put("settings", settings);
      List<Document> labels = (List<Document>) userEntity.get("labels");
      Map<String, String> newLabels = new HashMap<>();
      if (labels != null && !labels.isEmpty())
        for (final Document label : labels) {
          newLabels.put(label.getString("key"), label.getString("value"));
        }
      userEntity.replace("labels", newLabels);
      usersCollection.replaceOne(eq("_id", userEntity.getObjectId("_id")), userEntity);
      
      // Migrate all prior Workflow to User Relationships
      Bson wfQuery1 = Filters.eq("type", "BELONGSTO");
      Bson wfQuery2 = Filters.eq("from", "WORKFLOW");
      Bson wfQuery3 = Filters.eq("to", "USER");
      Bson wfQuery4 = Filters.eq("toRef", userEntity.get("_id").toString());
      Bson wfQueryAll = Filters.and(wfQuery1, wfQuery2, wfQuery3, wfQuery4);
      final FindIterable<Document> wfRelationships = relationshipsCollection.find(wfQueryAll);
      if (wfRelationships != null) {
        for (final Document eRel : wfRelationships) {
          eRel.put("to", "TEAM");
          eRel.put("toRef", newTeamId.toString());
          relationshipsCollection.replaceOne(eq("_id", eRel.getObjectId("_id")), eRel);
        }
      }
      
   // Migrate all prior Workflow to User Relationships
      Bson wfRunQuery1 = Filters.eq("type", "BELONGSTO");
      Bson wfRunQuery2 = Filters.eq("from", "WORKFLOWRUN");
      Bson wfRunQuery3 = Filters.eq("to", "USER");
      Bson wfRunQuery4 = Filters.eq("toRef", userEntity.get("_id").toString());
      Bson wfRunQueryAll = Filters.and(wfRunQuery1, wfRunQuery2, wfRunQuery3, wfRunQuery4);
      final FindIterable<Document> wfRunRelationships = relationshipsCollection.find(wfRunQueryAll);
      if (wfRunRelationships != null) {
        for (final Document eRel : wfRunRelationships) {
          eRel.put("to", "TEAM");
          eRel.put("toRef", newTeamId.toString());
          relationshipsCollection.replaceOne(eq("_id", eRel.getObjectId("_id")), eRel);
        }
      }
    }
  }

  /*
   * Migrate System to new System Team
   * 
   * - Create new "system" team. Will mark it as never be able to delete.
   * - Change relationship string
   * 
   * Note: this needs to happen after migrating Teams and Users
   */
  @ChangeSet(order = "4015", id = "4015", author = "Tyson Lawrie")
  public void v4MigrateSystemToATeam(MongoDatabase db) throws IOException {
    logger.info("Migrating System");
    String teamsCollectionName = collectionPrefix + "teams";
    MongoCollection<Document> teamsCollection = db.getCollection(teamsCollectionName);
    
    String relationshipsCollectionName = collectionPrefix + "relationships";
    MongoCollection<Document> relationshipsCollection = db.getCollection(relationshipsCollectionName);
    
    String usersCollectionName = collectionPrefix + "users";
    MongoCollection<Document> usersCollection = db.getCollection(usersCollectionName);
    
    Document team = new Document();
    team.put("name", "system");
    Document quotas = new Document();
    quotas.put("maxWorkflowCount", Integer.MAX_VALUE);
    quotas.put("maxWorkflowExecutionMonthly", Integer.MAX_VALUE);
    quotas.put("maxWorkflowStorage", Integer.MAX_VALUE);
    quotas.put("maxWorkflowExecutionTime", Integer.MAX_VALUE);
    quotas.put("maxConcurrentWorkflows", Integer.MAX_VALUE);
    team.put("quotas", quotas);
    team.put("status", "active");
    team.put("creationDate", new Date());
    ObjectId newTeamId = new ObjectId();
    team.put("_id", newTeamId);
    teamsCollection.insertOne(team);


    // Migrate all prior System Workflows to Team Relationships
    Bson wfQuery1 = Filters.eq("type", "BELONGSTO");
    Bson wfQuery2 = Filters.eq("from", "WORKFLOW");
    Bson wfQuery3 = Filters.eq("to", "SYSTEM");
    Bson wfQueryAll = Filters.and(wfQuery1, wfQuery2, wfQuery3);
    final FindIterable<Document> wfRelationships = relationshipsCollection.find(wfQueryAll);
    if (wfRelationships != null) {
      for (final Document eRel : wfRelationships) {
        eRel.put("to", "TEAM");
        eRel.put("toRef", newTeamId.toString());
        relationshipsCollection.replaceOne(eq("_id", eRel.getObjectId("_id")), eRel);
      }
    }
    
 // Migrate all prior Workflow to User Relationships
    Bson wfRunQuery1 = Filters.eq("type", "BELONGSTO");
    Bson wfRunQuery2 = Filters.eq("from", "WORKFLOWRUN");
    Bson wfRunQuery3 = Filters.eq("to", "SYSTEM");
    Bson wfRunQueryAll = Filters.and(wfRunQuery1, wfRunQuery2, wfRunQuery3);
    final FindIterable<Document> wfRunRelationships = relationshipsCollection.find(wfRunQueryAll);
    if (wfRunRelationships != null) {
      for (final Document eRel : wfRunRelationships) {
        eRel.put("to", "TEAM");
        eRel.put("toRef", newTeamId.toString());
        relationshipsCollection.replaceOne(eq("_id", eRel.getObjectId("_id")), eRel);
      }
    }

    final FindIterable<Document> userEntities = usersCollection.find();
    for (final Document userEntity : userEntities) {
      String type = (String) userEntity.get("type");
      if (type.equals("admin")) {
        Document newTeamRelationship = new Document();
        newTeamRelationship.put("type", "MEMBEROF");
        newTeamRelationship.put("from", "USER");
        newTeamRelationship.put("fromRef", userEntity.get("_id").toString());
        newTeamRelationship.put("to", "TEAM");
        newTeamRelationship.put("toRef", newTeamId.toString());
        relationshipsCollection.insertOne(newTeamRelationship);
      }
    }
  }

  /*
   * Migrate Template Workflows to new entity
   * 
   * - Create new "system" team. Will mark it as never be able to delete.
   * - Change relationship string
   * 
   * Note: this needs to happen after migrating Teams and Users
   * 
   */
  @ChangeSet(order = "4016", id = "4016", author = "Tyson Lawrie")
  public void v4MigrateTemplateWorkflows(MongoDatabase db) throws IOException {
    logger.info("Migrating Templates");
  String workflowsCollectionName = collectionPrefix + "workflows";
    MongoCollection<Document> workflowsCollection = db.getCollection(workflowsCollectionName);
    

    String revisionCollectionName = collectionPrefix + "workflow_revisions";
    MongoCollection<Document> workflowRevisionsCollection =
        db.getCollection(revisionCollectionName);
    
  String wfTemplatesCollectionName = collectionPrefix + "workflow_templates";
    MongoCollection<Document> wfTemplatesCollection = db.getCollection(wfTemplatesCollectionName);
    if (wfTemplatesCollection == null) {
      db.createCollection(wfTemplatesCollectionName);
    }
    wfTemplatesCollection = db.getCollection(wfTemplatesCollectionName);
    
    String relationshipsCollectionName = collectionPrefix + "relationships";
    MongoCollection<Document> relationshipsCollection = db.getCollection(relationshipsCollectionName);

    // Migrate all TEMPLATE Workflows to new Workflow_Template collection
    Bson query1 = Filters.eq("type", "BELONGSTO");
    Bson query2 = Filters.eq("from", "WORKFLOW");
    Bson query3 = Filters.eq("to", "TEMPLATE");
    Bson queryAll = Filters.and(query1, query2, query3);
    logger.info("Migrating Templates - WORKFLOW Relationship Filter: " + queryAll.toString());
    final FindIterable<Document> wfTemplateRelationships = relationshipsCollection.find(queryAll);
    if (wfTemplateRelationships != null) {
      for (final Document eRel : wfTemplateRelationships) {
        String workflowId = eRel.get("fromRef").toString();
        logger.info("Migrating Templates - Template ID: " + workflowId);
        Document wfTemplate = workflowsCollection.find(eq("_id", new ObjectId(workflowId))).first();
        final FindIterable<Document> wfRevisions =  workflowRevisionsCollection.find(eq("workflowRef", workflowId));
        for (final Document revision : wfRevisions) {
          // Create new WorkflowTemplateEntity using a combination of WorkflowEntity and WorkflowRevisionEntity
          // Don't add triggers and remove workflowRef
          logger.info("Migrating Templates - Revision: " + revision.toString());
          revision.put("name", wfTemplate.get("name").toString().toLowerCase().replace(' ', '-'));
          revision.put("displayName", wfTemplate.get("name"));
          revision.put("creationDate", wfTemplate.get("creationDate"));
          revision.put("icon", wfTemplate.get("icon"));
          revision.put("description", wfTemplate.get("description"));
          revision.put("shortDescription", wfTemplate.get("shortDescription"));
          revision.put("labels", wfTemplate.get("labels"));
          Map<String, Object> annotations = new HashMap<>();
          annotations.put("io#boomerang/generation", "3");
          annotations.put("io#boomerang/kind", "WorkflowTemplate");
          revision.put("annotations", annotations);
          revision.remove("workflowRef");
          wfTemplatesCollection.insertOne(revision);
          workflowRevisionsCollection.deleteOne(eq("_id", revision.get("_id")));
        }
        workflowsCollection.deleteOne(eq("_id", new ObjectId(workflowId)));
        relationshipsCollection.deleteOne(eq("_id", eRel.getObjectId("_id")));
      }
    }
  }

  /*
   * Migrate Workflow Schedules to new entity
   */
  @ChangeSet(order = "4017", id = "4017", author = "Tyson Lawrie")
  public void v4MigrateWorkflowSchedules(MongoDatabase db) throws IOException {
    logger.info("Migrating Workflow Schedules");
  String origWorkflowSchedulesCollectionName = collectionPrefix + "workflows_schedules";
    MongoCollection<Document> origWorkflowSchedulesCollection = db.getCollection(origWorkflowSchedulesCollectionName);
    
  String newWorkflowSchedulesCollectionName = collectionPrefix + "workflow_schedules";
    MongoCollection<Document> newWorkflowSchedulesCollection = db.getCollection(newWorkflowSchedulesCollectionName);
    if (newWorkflowSchedulesCollection == null) {
      db.createCollection(newWorkflowSchedulesCollectionName);
    }
    newWorkflowSchedulesCollection = db.getCollection(newWorkflowSchedulesCollectionName);

    final FindIterable<Document> scheduleEntities = newWorkflowSchedulesCollection.find();
    for (final Document scheduleEntity : scheduleEntities) {
      // Change from ID to Ref based linkage
      scheduleEntity.put("workflowRef", scheduleEntity.get("workflowId"));
      scheduleEntity.remove("workflowId");
      
      // Convert Labels
      List<Document> labels = (List<Document>) scheduleEntity.get("labels");
      Map<String, String> newLabels = new HashMap<>();
      if (labels != null) {
        for (final Document label : labels) {
          newLabels.put(label.getString("key"), label.getString("value"));
        }
        scheduleEntity.replace("labels", newLabels);
      } else {
        scheduleEntity.put("labels", newLabels);
      }

      // Convert parameters from List<KeyValuePair> to List<RunParam>
      List<Document> parameters = (List<Document>) scheduleEntity.get("parameters");
      List<Document> params = new LinkedList<>();
      if (parameters != null && !parameters.isEmpty()) {
        for (final Document parameter : parameters) {
          Document param = new Document();
          param.put("name", parameter.get("key"));
          param.put("value", parameter.get("value"));
          param.put("type", parameter.get("string"));
          params.add(param);
        }
      }
      scheduleEntity.put("params", params);
      scheduleEntity.remove("properties");
      
      newWorkflowSchedulesCollection.insertOne(scheduleEntity);
    }
    origWorkflowSchedulesCollection.drop();
  }

   /*
    * Drop all legacy tokens - no migration path
    */
   @ChangeSet(order = "4018", id = "4018", author = "Tyson Lawrie")
   public void v4DropLegacyTokens(MongoDatabase db) throws IOException {
     logger.info("Drop Legacy Tokens");
   String tokensCollectionName = collectionPrefix + "tokens";
     MongoCollection<Document> tokensCollection = db.getCollection(tokensCollectionName);
     
     tokensCollection.drop();
     db.createCollection(tokensCollectionName);
   }

   /*
    * Drop User Quota Settings
    */
   @ChangeSet(order = "4019", id = "4019", author = "Tyson Lawrie")
   public void v4DropUserQuotaSettings(MongoDatabase db) throws IOException {
     logger.info("Drop User Quota Settings");
   String collectionName = collectionPrefix + "settings";
     MongoCollection<Document> collection = db.getCollection(collectionName);
     
     collection.deleteOne(eq("_id", "6123c1e20b07a54cdce637c0"));
   }

   /*
    * Drop User Quota Settings
    */
   @ChangeSet(order = "4020", id = "4020", author = "Tyson Lawrie")
   public void v4AdjustSettingsKeys(MongoDatabase db) throws IOException {
     logger.info("Adjust Workspace Settings Keys");
   String collectionName = collectionPrefix + "settings";
     MongoCollection<Document> collection = db.getCollection(collectionName);
     
     Document workflowRunWorkspaceConfig = (Document) collection.find(eq("_id",  new ObjectId("60245957226920beece4fdf9"))).first();
     workflowRunWorkspaceConfig.replace("key", "workflowrun");
     collection.replaceOne(eq("_id", new ObjectId("60245957226920beece4fdf9")), workflowRunWorkspaceConfig);
     
     Document taskConfig = (Document) collection.find(eq("_id", new ObjectId("5f32cb19d09662744c0df51d"))).first();
     taskConfig.replace("key", "task");
     collection.replaceOne(eq("_id", new ObjectId("5f32cb19d09662744c0df51d")), taskConfig);
   }
}
