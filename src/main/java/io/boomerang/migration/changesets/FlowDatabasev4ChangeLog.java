package io.boomerang.migration.changesets;

import static com.mongodb.client.model.Filters.eq;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
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
import com.mongodb.MongoNamespace;
import com.mongodb.client.FindIterable;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.Aggregates;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.GraphLookupOptions;
import com.mongodb.client.model.IndexOptions;
import com.mongodb.client.model.Indexes;
import com.mongodb.client.model.RenameCollectionOptions;
import com.mongodb.client.result.InsertOneResult;
import io.boomerang.migration.FileLoadingService;
import io.boomerang.migration.SpringContextBridge;

@ChangeLog
public class FlowDatabasev4ChangeLog {

  private static FileLoadingService fileloadingService;

  private static String ANNOTATION_PREFIX = "boomerang#io";

  private static String workflowCollectionPrefix;

  private static boolean mongoCosmosDBTTL;


  private final Logger LOGGER = LoggerFactory.getLogger(FlowDatabasev4ChangeLog.class);

  public FlowDatabasev4ChangeLog() {
    fileloadingService = SpringContextBridge.services().getFileLoadingService();
    workflowCollectionPrefix = SpringContextBridge.services().getCollectionPrefix();
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
    LOGGER.info("Commencing v4 Migration Change Sets...");
    String origCollectionName = workflowCollectionPrefix + "tasks_locks";
    String newCollectionName = workflowCollectionPrefix + "locks";

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
    LOGGER.info("Dropping Workflow Task Activity");
    String collectionName = workflowCollectionPrefix + "workflows_activity_task";
    db.getCollection(collectionName).drop();
  }

  /*
   * Partially migrates workflow activity so that insights and activity works at a high level.
   * 
   * Unable to migrate Workflow Activity Storage to WorkflowRun Workspaces as they werent stored in
   * the old model
   */
  @ChangeSet(order = "4002", id = "4002", author = "Tyson Lawrie")
  public void v4MigrateWorkflowActivity(MongoDatabase db) throws IOException {
    LOGGER.info("Migrating Relationships");
    String relationshipCollectionName = workflowCollectionPrefix + "relationships";
    MongoCollection<Document> relationshipCollection = db.getCollection(relationshipCollectionName);
    if (relationshipCollection == null) {
      db.createCollection(relationshipCollectionName);
    }
    relationshipCollection = db.getCollection(relationshipCollectionName);

    String newCollectionName = workflowCollectionPrefix + "workflow_runs";
    MongoCollection<Document> workflowRunsCollection = db.getCollection(newCollectionName);
    if (workflowRunsCollection == null) {
      db.createCollection(newCollectionName);
    }
    workflowRunsCollection = db.getCollection(newCollectionName);

    String collectionName = workflowCollectionPrefix + "workflows_activity";
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
      annotations.put(ANNOTATION_PREFIX + "/generation", "3");
      annotations.put(ANNOTATION_PREFIX + "/kind", "WorkflowRun");
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
      List<Document> outputProperties =
          (List<Document>) workflowsActivityEntity.get("outputProperties");
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
        relationship.put("toType",
            StringUtils.capitalize((String) workflowsActivityEntity.get("scope")));
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
    LOGGER.info("Migrating Actions");
    String newCollectionName = workflowCollectionPrefix + "actions";
    MongoCollection<Document> workflowActionsCollection = db.getCollection(newCollectionName);
    if (workflowActionsCollection == null) {
      db.createCollection(newCollectionName);
    }
    workflowActionsCollection = db.getCollection(newCollectionName);

    String collectionName = workflowCollectionPrefix + "workflows_activity_approval";
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
    LOGGER.info("Migrating Task Templates");
    String relationshipCollectionName = workflowCollectionPrefix + "relationships";
    MongoCollection<Document> relationshipCollection = db.getCollection(relationshipCollectionName);
    if (relationshipCollection == null) {
      db.createCollection(relationshipCollectionName);
    }
    relationshipCollection = db.getCollection(relationshipCollectionName);

    MongoCollection<Document> taskTemplatesCollection =
        db.getCollection(workflowCollectionPrefix + "task_templates");

    MongoCollection<Document> taskTemplateRevisionsCollection =
        db.getCollection(workflowCollectionPrefix + "task_template_revisions");

    final FindIterable<Document> taskTemplateEntities = taskTemplatesCollection.find();
    for (final Document taskTemplateEntity : taskTemplateEntities) {
      LOGGER.info("Found template: " + taskTemplateEntity.get("name").toString());
      // Create TaskTemplateEntity
      Document newTaskTemplateEntity = new Document();
      newTaskTemplateEntity.put("name",
          taskTemplateEntity.get("name").toString().trim().toLowerCase().replace(' ', '-'));
      newTaskTemplateEntity.put("status", taskTemplateEntity.get("status"));
      newTaskTemplateEntity.put("verified", taskTemplateEntity.get("verified"));
      Map<String, String> labels = new HashMap<>();
      newTaskTemplateEntity.put("labels", labels);
      Map<String, Object> annotations = new HashMap<>();
      annotations.put(ANNOTATION_PREFIX + "/generation", "3");
      annotations.put(ANNOTATION_PREFIX + "/kind", "Task");
      newTaskTemplateEntity.put("annotations", annotations);
      newTaskTemplateEntity.put("creationDate", taskTemplateEntity.get("createdDate"));
      if ("templateTask".equals(taskTemplateEntity.get("nodetype"))) {
        newTaskTemplateEntity.put("type", "template");
      } else if ("customTask".equals(taskTemplateEntity.get("nodetype"))) {
        newTaskTemplateEntity.put("type", "custom");
      } else {
        newTaskTemplateEntity.put("type", taskTemplateEntity.get("nodetype"));
      }

      LOGGER.info("Migrating template: " + taskTemplateEntity.get("name").toString());
      newTaskTemplateEntity.put("_id", taskTemplateEntity.get("_id"));
      taskTemplatesCollection.replaceOne(eq("_id", taskTemplateEntity.getObjectId("_id")),
          newTaskTemplateEntity);

      // Convert scope & owner to relationship
      Document relationship = new Document();
      relationship.put("relationship", "belongs-to");
      relationship.put("fromType", "TaskTemplate");
      relationship.put("fromRef", newTaskTemplateEntity.get("name").toString());
      if ("team".equals((String) taskTemplateEntity.get("scope"))) {
        relationship.put("toType", "Team");
        relationship.put("toRef", taskTemplateEntity.get("flowTeamId"));
      } else {
        relationship.put("toType", "Global");
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

      // Create TasktemplateRevisionEntity
      Document newTaskTemplateRevisionEntity = new Document();
      newTaskTemplateRevisionEntity.put("parent", newTaskTemplateEntity.get("name"));
      newTaskTemplateRevisionEntity.put("displayName", taskTemplateEntity.get("name"));
      newTaskTemplateRevisionEntity.put("description", taskTemplateEntity.get("description"));
      newTaskTemplateRevisionEntity.put("category", taskTemplateEntity.get("category"));
      newTaskTemplateRevisionEntity.put("icon", taskTemplateEntity.get("icon"));

      List<Document> revisions = (List<Document>) taskTemplateEntity.get("revisions");
      if (revisions != null && !revisions.isEmpty()) {
        for (final Document revision : revisions) {
          newTaskTemplateRevisionEntity.put("version", revision.get("version"));
          newTaskTemplateRevisionEntity.put("changelog", revision.get("changelog"));
          List<Document> configs = (List<Document>) revision.get("config");
          newTaskTemplateRevisionEntity.put("config", configs);
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
          newTaskTemplateRevisionEntity.put("spec", spec);

          LOGGER.info("Inserting template revision: " + newTaskTemplateEntity.get("name").toString()
              + "@" + revision.get("version").toString());
          ObjectId newId = new ObjectId();
          newTaskTemplateRevisionEntity.put("_id", newId);
          taskTemplateRevisionsCollection.insertOne(newTaskTemplateRevisionEntity);
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
    String relationshipCollectionName = workflowCollectionPrefix + "relationships";
    MongoCollection<Document> relationshipCollection = db.getCollection(relationshipCollectionName);
    if (relationshipCollection == null) {
      db.createCollection(relationshipCollectionName);
    }
    relationshipCollection = db.getCollection(relationshipCollectionName);

    String taskTemplateCollectionName = workflowCollectionPrefix + "task_templates";
    MongoCollection<Document> taskTemplatesCollection =
        db.getCollection(taskTemplateCollectionName);

    String newRevisionCollectionName = workflowCollectionPrefix + "workflow_revisions";
    MongoCollection<Document> workflowRevisionsCollection =
        db.getCollection(newRevisionCollectionName);
    if (workflowRevisionsCollection == null) {
      db.createCollection(newRevisionCollectionName);
    }
    workflowRevisionsCollection = db.getCollection(newRevisionCollectionName);

    String revisionCollectionName = workflowCollectionPrefix + "workflows_revisions";
    MongoCollection<Document> workflowsRevisionsCollection =
        db.getCollection(revisionCollectionName);
    final FindIterable<Document> workflowsRevisionEntities = workflowsRevisionsCollection.find();

    String workflowsCollectionName = workflowCollectionPrefix + "workflows";
    MongoCollection<Document> workflowsCollection = db.getCollection(workflowsCollectionName);

    final FindIterable<Document> workflowsEntities = workflowsCollection.find();
    for (final Document workflowsEntity : workflowsEntities) {
      LOGGER.info("Migrating WorkflowId: " + workflowsEntity.get("_id"));
      
      // Remove legacy tokens
      workflowsEntity.remove("tokens");

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
      annotations.put(ANNOTATION_PREFIX + "/generation", "3");
      annotations.put(ANNOTATION_PREFIX + "/kind", "Workflow");
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
        LOGGER.info("Migrating Workflow Revision: " + workflowRevisionEntity.get("_id"));
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
              dependency.put("decisionCondition",
                  dependency.get("switchCondition") != null ? dependency.get("switchCondition")
                      : "");
              Document dependentTask = dagTasksRef.stream()
                  .filter(e -> e.get("taskId").equals(dependency.get("taskId"))).findFirst().get();
              // Start / End nodes did not have labels in v3. Cannot be dependent on End node.
              if (dependentTask.getString("type").equals("start")) {
                dependency.put("taskRef", "start");
              } else {
                dependency.put("taskRef", dependentTask.get("label"));
              }
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
            taskAnnotations.put(ANNOTATION_PREFIX + "/position", metadata.get("position"));
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
    LOGGER.info("Create CosmosDB Sorting Indexes");
    String workflowsCollectionName = workflowCollectionPrefix + "workflows";
    MongoCollection<Document> workflowsCollection = db.getCollection(workflowsCollectionName);
    workflowsCollection.createIndex(Indexes.descending("creationDate"));

    String workflowRevisionsCollectionName = workflowCollectionPrefix + "workflow_revisions";
    MongoCollection<Document> workflowRevisionsCollection =
        db.getCollection(workflowRevisionsCollectionName);
    workflowRevisionsCollection.createIndex(Indexes.descending("version"));
    workflowRevisionsCollection.createIndex(
        Indexes.compoundIndex(Indexes.descending("workflowRef"), Indexes.descending("version")));

    String taskTemplatesCollectionName = workflowCollectionPrefix + "task_templates";
    MongoCollection<Document> taskTemplatesCollection =
        db.getCollection(taskTemplatesCollectionName);
    taskTemplatesCollection.createIndex(Indexes.descending("creationDate"));

    String taskRunsCollectionName = workflowCollectionPrefix + "task_runs";
    MongoCollection<Document> taskRunsCollection = db.getCollection(taskRunsCollectionName);
    taskRunsCollection.createIndex(Indexes.descending("creationDate"));

    String workflowRunsCollectionName = workflowCollectionPrefix + "workflow_runs";
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
    LOGGER.info("Create Relationships Collection");
    String relationshipCollectionName = workflowCollectionPrefix + "relationships";
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
    LOGGER.info("Create Indexes");
    String taskRunsCollectionName = workflowCollectionPrefix + "task_runs";
    MongoCollection<Document> taskRunsCollection = db.getCollection(taskRunsCollectionName);
    try {
      taskRunsCollection.createIndex(Indexes.ascending("labels.$**"));
      taskRunsCollection.createIndex(Indexes.descending("status"));
      taskRunsCollection.createIndex(Indexes.descending("phase"));
    } catch (Exception e) {

    }

    String workflowRunsCollectionName = workflowCollectionPrefix + "workflow_runs";
    MongoCollection<Document> workflowRunsCollection = db.getCollection(workflowRunsCollectionName);
    try {
      workflowRunsCollection.createIndex(Indexes.ascending("labels.$**"));
      workflowRunsCollection.createIndex(Indexes.descending("status"));
      workflowRunsCollection.createIndex(Indexes.descending("phase"));
    } catch (Exception e) {

    }
  }

  /*
   * Creates additional indexes for the TaskRun & WorkflowRun Query.
   * 
   * While the prior v4 loaders do reference this collection, it was introduced after the loader had
   * been used by the community Hence we do safe check creation.
   */
  @ChangeSet(order = "4009", id = "4009", author = "Tyson Lawrie")
  public void v4CreateQueryIndexes2(MongoDatabase db) throws IOException {
    LOGGER.info("Create Additional Indexes");
    String taskRunsCollectionName = workflowCollectionPrefix + "task_runs";
    MongoCollection<Document> taskRunsCollection = db.getCollection(taskRunsCollectionName);
    taskRunsCollection.createIndex(Indexes.descending("name"));
    taskRunsCollection.createIndex(Indexes.descending("workflowRunRef"));

    String taskTemplatesCollectionName = workflowCollectionPrefix + "task_templates";
    MongoCollection<Document> taskTemplatesCollection =
        db.getCollection(taskTemplatesCollectionName);
    taskTemplatesCollection.createIndex(Indexes.descending("name"));
    taskTemplatesCollection.createIndex(Indexes.descending("parent"));
    taskTemplatesCollection.createIndex(Indexes.descending("version"));
  }

  /*
   * Load in v2 of the Sleep TaskTemplate migrating to a system task
   */
  @ChangeSet(order = "4010", id = "4010", author = "Tyson Lawrie")
  public void updateSleepTaskTemplate(MongoDatabase db) throws IOException {
    LOGGER.info("Update Sleep Task Template");
    final List<String> taskTemplates =
        fileloadingService.loadFiles("flow/4010/task_templates/*.json");
    for (final String template : taskTemplates) {
      final Document doc = Document.parse(template);
      final MongoCollection<Document> collection =
          db.getCollection(workflowCollectionPrefix + "task_templates");
      collection.replaceOne(eq("_id", doc.getObjectId("_id")), doc);
    }
    final List<String> taskTemplateRevisions =
        fileloadingService.loadFiles("flow/4010/task_template_revisions/*.json");
    for (final String revision : taskTemplateRevisions) {
      final Document doc = Document.parse(revision);
      final MongoCollection<Document> collection =
          db.getCollection(workflowCollectionPrefix + "task_template_revisions");
      collection.deleteOne(eq("parent", "sleep"));
      collection.insertOne(doc);
    }
  }

  /*
   * Migrate Teams
   * 
   * Need to check if Teams collection exists as for IBM Services Essentials it may not with the
   * change to internalised teams.
   * 
   */
  @ChangeSet(order = "4011", id = "4011", author = "Tyson Lawrie")
  public void v4MigrateTeams(MongoDatabase db) throws IOException {
    LOGGER.info("Migrating Teams");
    String relationshipCollectionName = workflowCollectionPrefix + "relationships";
    MongoCollection<Document> relationshipCollection = db.getCollection(relationshipCollectionName);

    String teamsCollectionName = workflowCollectionPrefix + "teams";
    MongoCollection<Document> teamsCollection = db.getCollection(teamsCollectionName);
    if (teamsCollection == null) {
      db.createCollection(teamsCollectionName);
    }
    teamsCollection = db.getCollection(teamsCollectionName);

    final FindIterable<Document> teamsEntities = teamsCollection.find();
    for (final Document teamsEntity : teamsEntities) {
      LOGGER.info("Migrating Team - ID: " + teamsEntity.get("_id"));

      teamsEntity.put("creationDate", new Date());

      // Migrate Name
      teamsEntity.put("displayName", teamsEntity.get("name"));
      String uniqueName = teamsEntity.get("name").toString();
      uniqueName = uniqueName.replaceAll("[^A-Za-z0-9' \\-]", "");
      uniqueName = uniqueName.replaceAll("\\s+", "-");
      uniqueName = uniqueName.replaceAll("'", "-");
      uniqueName = uniqueName.replaceAll("\\-+", "-");
      teamsEntity.replace("name", uniqueName.toLowerCase());

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
      teamsEntity.put("externalRef", teamsEntity.get("higherLevelGroupId"));
      teamsEntity.remove("higherLevelGroupId");

      // Migrate Status
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
            settings.get("properties") != null ? settings.get("properties") : new LinkedList<>());
      } else {
        teamsEntity.put("parameters", new LinkedList<>());
      }
      teamsEntity.remove("settings");

      // Migrate ApproverGroups
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
          approverGroup.put("approvers", approverRefs);
          approverGroup.remove("approvers");
          ObjectId newId = new ObjectId();
          approverGroup.put("_id", newId);

          // Add relationship between Team and ApproverGroup
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
      teamsCollection.replaceOne(eq("_id", teamsEntity.getObjectId("_id")), teamsEntity);
    }
  }

  /*
   * Migrate Relationships in case prior loaders were run in early Beta versions
   * 
   * - Add creationDate - Change relationship string
   * 
   */
  @ChangeSet(order = "4012", id = "4012", author = "Tyson Lawrie")
  public void v4MigrateRelationships(MongoDatabase db) throws IOException {
    LOGGER.info("Migrating Relationships");
    String relationshipCollectionName = workflowCollectionPrefix + "relationships";
    MongoCollection<Document> relationshipCollection = db.getCollection(relationshipCollectionName);
    final FindIterable<Document> relationshipEntities = relationshipCollection.find();
    for (final Document relationshipEntity : relationshipEntities) {

      if (relationshipEntity.get("creationDate") == null) {
        relationshipEntity.put("creationDate", new Date());
      }
      if (relationshipEntity.get("relationship") != null
          && "belongs-to".equals(relationshipEntity.get("relationship").toString())) {
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
      if (relationshipEntity.get("to") != null) {
        relationshipEntity.put("to", relationshipEntity.get("to").toString().toUpperCase());
      }
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
   * - Remove userName (PI) - Rename userId to Author
   * 
   */
  @ChangeSet(order = "4013", id = "4013", author = "Tyson Lawrie")
  public void v4MigrateChangelog(MongoDatabase db) throws IOException {
    LOGGER.info("Migrating Workflow Revision Changelogs");
    String revisionCollectionName = workflowCollectionPrefix + "workflow_revisions";
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
          workflowRevisionsCollection.replaceOne(
              eq("_id", workflowRevisionEntity.getObjectId("_id")), workflowRevisionEntity);
        }
      }
    }

    MongoCollection<Document> taskTemplatesCollection =
        db.getCollection(workflowCollectionPrefix + "task_template_revisions");

    LOGGER.info("Migrating TaskTemplateRevision Changelogs");
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
   * - Each User needs to have a uniquely named team - Migrate users teams list to Relationships -
   * Any previous Workflow Relationship to a User, needs to be to the new Team
   * 
   */
  @ChangeSet(order = "4014", id = "4014", author = "Tyson Lawrie")
  public void v4MigrateUsersToTeam(MongoDatabase db) throws IOException {
    String usersCollectionName = workflowCollectionPrefix + "users";
    MongoCollection<Document> usersCollection = db.getCollection(usersCollectionName);

    String teamsCollectionName = workflowCollectionPrefix + "teams";
    MongoCollection<Document> teamsCollection = db.getCollection(teamsCollectionName);

    String relationshipsCollectionName = workflowCollectionPrefix + "relationships";
    MongoCollection<Document> relationshipsCollection =
        db.getCollection(relationshipsCollectionName);

    final FindIterable<Document> userEntities = usersCollection.find();
    for (final Document userEntity : userEntities) {
      LOGGER.info("Migrating Users - ID: " + userEntity.get("_id").toString());
      String userName = userEntity.getString("name");
      Document team = new Document();
      if (userEntity.get("quotas") != null) {
        Document quotas = (Document) userEntity.get("quotas");
        userEntity.remove("quotes");
        team.put("quotas", quotas);
      } else {
        Document quotas = new Document();
        quotas.put("maxWorkflowCount", 10);
        quotas.put("maxWorkflowExecutionMonthly", 20);
        quotas.put("maxWorkflowStorage", 25);
        quotas.put("maxWorkflowExecutionTime", 30);
        quotas.put("maxConcurrentWorkflows", 4);
        team.put("quotas", quotas);
      }
      String teamName = userName.replace("@", "-").replace(".", "-") + " Personal Team";
      team.put("displayName", teamName);
      teamName = teamName.replaceAll("[^A-Za-z0-9' \\-]", "");
      teamName = teamName.replaceAll("\\s+", "-");
      teamName = teamName.replaceAll("'", "-");
      teamName = teamName.replaceAll("\\-+", "-");
      team.put("name", teamName.toLowerCase());
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
      if (labels != null && !labels.isEmpty()) {
        for (final Document label : labels) {
          newLabels.put(label.getString("key"), label.getString("value"));
        }
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
   * - Create new "system" team. Will mark it as never be able to delete. - Change relationship
   * string
   * 
   * Note: this needs to happen after migrating Teams and Users
   */
  @ChangeSet(order = "4015", id = "4015", author = "Tyson Lawrie")
  public void v4MigrateSystemToATeam(MongoDatabase db) throws IOException {
    LOGGER.info("Migrating System");
    String teamsCollectionName = workflowCollectionPrefix + "teams";
    MongoCollection<Document> teamsCollection = db.getCollection(teamsCollectionName);

    String relationshipsCollectionName = workflowCollectionPrefix + "relationships";
    MongoCollection<Document> relationshipsCollection =
        db.getCollection(relationshipsCollectionName);

    String usersCollectionName = workflowCollectionPrefix + "users";
    MongoCollection<Document> usersCollection = db.getCollection(usersCollectionName);

    Document team = new Document();
    team.put("name", "system");
    team.put("displayName", "System and Administration");
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
   * - Create new "system" team. Will mark it as never be able to delete. - Change relationship
   * string
   * 
   * Note: this needs to happen after migrating Teams and Users
   * 
   */
  @ChangeSet(order = "4016", id = "4016", author = "Tyson Lawrie")
  public void v4MigrateTemplateWorkflows(MongoDatabase db) throws IOException {
    LOGGER.info("Migrating Templates");
    String workflowsCollectionName = workflowCollectionPrefix + "workflows";
    MongoCollection<Document> workflowsCollection = db.getCollection(workflowsCollectionName);


    String revisionCollectionName = workflowCollectionPrefix + "workflow_revisions";
    MongoCollection<Document> workflowRevisionsCollection =
        db.getCollection(revisionCollectionName);

    String wfTemplatesCollectionName = workflowCollectionPrefix + "workflow_templates";
    MongoCollection<Document> wfTemplatesCollection = db.getCollection(wfTemplatesCollectionName);
    if (wfTemplatesCollection == null) {
      db.createCollection(wfTemplatesCollectionName);
    }
    wfTemplatesCollection = db.getCollection(wfTemplatesCollectionName);

    String relationshipsCollectionName = workflowCollectionPrefix + "relationships";
    MongoCollection<Document> relationshipsCollection =
        db.getCollection(relationshipsCollectionName);

    // Migrate all TEMPLATE Workflows to new Workflow_Template collection
    Bson query1 = Filters.eq("type", "BELONGSTO");
    Bson query2 = Filters.eq("from", "WORKFLOW");
    Bson query3 = Filters.eq("to", "TEMPLATE");
    Bson queryAll = Filters.and(query1, query2, query3);
    final FindIterable<Document> wfTemplateRelationships = relationshipsCollection.find(queryAll);
    if (wfTemplateRelationships != null) {
      for (final Document eRel : wfTemplateRelationships) {
        String workflowId = eRel.get("fromRef").toString();
        LOGGER.info("Migrating Templates - Template ID: " + workflowId);
        Document wfTemplate = workflowsCollection.find(eq("_id", new ObjectId(workflowId))).first();
        final FindIterable<Document> wfRevisions =
            workflowRevisionsCollection.find(eq("workflowRef", workflowId));
        for (final Document revision : wfRevisions) {
          // Create new WorkflowTemplateEntity using a combination of WorkflowEntity and
          // WorkflowRevisionEntity
          // Don't add triggers and remove workflowRef
          revision.put("name",
              wfTemplate.get("name").toString().trim().toLowerCase().replace(' ', '-'));
          revision.put("displayName", wfTemplate.get("name"));
          revision.put("creationDate", wfTemplate.get("creationDate"));
          revision.put("icon", wfTemplate.get("icon"));
          // Migrate shortDescription to description if description is empty
          if (wfTemplate.get("description") != null
              && !wfTemplate.get("description").toString().isEmpty()) {
            revision.put("description", wfTemplate.get("description"));
          } else {
            revision.put("description", wfTemplate.get("shortDescription"));
          }
          revision.put("labels", wfTemplate.get("labels"));
          Map<String, Object> annotations = new HashMap<>();
          annotations.put(ANNOTATION_PREFIX + "/generation", "3");
          annotations.put(ANNOTATION_PREFIX + "/kind", "WorkflowTemplate");
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
    LOGGER.info("Migrating Workflow Schedules");
    String origWorkflowSchedulesCollectionName = workflowCollectionPrefix + "workflows_schedules";
    MongoCollection<Document> origWorkflowSchedulesCollection =
        db.getCollection(origWorkflowSchedulesCollectionName);

    String newWorkflowSchedulesCollectionName = workflowCollectionPrefix + "workflow_schedules";
    MongoCollection<Document> newWorkflowSchedulesCollection =
        db.getCollection(newWorkflowSchedulesCollectionName);
    if (newWorkflowSchedulesCollection == null) {
      db.createCollection(newWorkflowSchedulesCollectionName);
    }
    newWorkflowSchedulesCollection = db.getCollection(newWorkflowSchedulesCollectionName);

    final FindIterable<Document> scheduleEntities = origWorkflowSchedulesCollection.find();
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
    LOGGER.info("Drop Legacy Tokens");
    String tokensCollectionName = workflowCollectionPrefix + "tokens";
    MongoCollection<Document> tokensCollection = db.getCollection(tokensCollectionName);

    tokensCollection.drop();
    db.createCollection(tokensCollectionName);
  }

  /*
   * Drop User Quota Settings
   */
  @ChangeSet(order = "4019", id = "4019", author = "Tyson Lawrie")
  public void v4DropUserQuotaSettings(MongoDatabase db) throws IOException {
    LOGGER.info("Drop User Quota Settings");
    String collectionName = workflowCollectionPrefix + "settings";
    MongoCollection<Document> collection = db.getCollection(collectionName);

    collection.deleteOne(eq("_id", new ObjectId("6123c1e20b07a54cdce637c0")));
  }

  /*
   * Adjust the task settings
   */
  @ChangeSet(order = "4020", id = "4020", author = "Tyson Lawrie")
  public void v4AdjustSettingsKeys(MongoDatabase db) throws IOException {
    LOGGER.info("Adjust Workspace Settings Keys");
    String collectionName = workflowCollectionPrefix + "settings";
    MongoCollection<Document> collection = db.getCollection(collectionName);

    Document workflowRunWorkspaceConfig =
        (Document) collection.find(eq("_id", new ObjectId("60245957226920beece4fdf9"))).first();
    workflowRunWorkspaceConfig.replace("key", "workflowrun");
    collection.replaceOne(eq("_id", new ObjectId("60245957226920beece4fdf9")),
        workflowRunWorkspaceConfig);

    Document taskConfig =
        (Document) collection.find(eq("_id", new ObjectId("5f32cb19d09662744c0df51d"))).first();
    taskConfig.replace("key", "task");
    List<Document> configs = (List<Document>) taskConfig.get("config");
    if (configs != null && !configs.isEmpty()) {
      for (final Document config : configs) {
        if (config.get("key").equals("job.deletion.policy")) {
          config.replace("key", "deletion.policy");
        } else if (config.get("key").equals("enable.tasks")) {
          config.replace("key", "edit.verified");
        } else if (config.get("key").equals("task.timeout.configuration")) {
          config.replace("key", "default.timeout");
        } else if (config.get("key").equals("worker.image")) {
          config.replace("key", "default.image");
        } else if (config.get("key").equals("enable.debug")) {
          config.replace("key", "debug");
        }
      }
    }
    taskConfig.replace("config", configs);
    collection.replaceOne(eq("_id", new ObjectId("5f32cb19d09662744c0df51d")), taskConfig);
  }

  /*
   * Migrate Workflow Short Description
   */
  @ChangeSet(order = "4021", id = "4021", author = "Tyson Lawrie")
  public void v4MigrateWorkflowShortDescription(MongoDatabase db) throws IOException {
    LOGGER.info("Migrating Workflow descriptions");
    String workflowsCollectionName = workflowCollectionPrefix + "workflows";
    MongoCollection<Document> collection = db.getCollection(workflowsCollectionName);

    final FindIterable<Document> workflowEntities = collection.find();
    for (final Document entity : workflowEntities) {

      // Migrate shortDescription to description if description is empty
      if (entity.get("description") != null && !entity.get("description").toString().isEmpty()) {
        entity.replace("description", entity.get("description"));
      } else {
        entity.put("description", entity.get("shortDescription"));
      }
      entity.remove("shortDescription");
      collection.replaceOne(eq("_id", entity.getObjectId("_id")), entity);
    }
  }

  /*
   * Migrate Quartz Jobs
   */
  @ChangeSet(order = "4022", id = "4022", author = "Tyson Lawrie")
  public void v4MigrateQuartzJobs(MongoDatabase db) throws IOException {
    LOGGER.info("Migrating Quartz Jobs");
    String collectionName = workflowCollectionPrefix + "jobs";
    MongoCollection<Document> collection = db.getCollection(collectionName);

    final FindIterable<Document> entities = collection.find();
    for (final Document entity : entities) {
      entity.replace("jobClass", "io.boomerang.quartz.QuartzSchedulerJob");
      collection.replaceOne(eq("_id", entity.getObjectId("_id")), entity);
    }
  }

  /*
   * Load Roles
   */
  @ChangeSet(order = "4023", id = "4023", author = "Tyson Lawrie")
  public void loadRoles(MongoDatabase db) throws IOException {
    LOGGER.info("Loading Roles");
    final List<String> files = fileloadingService.loadFiles("flow/4023/*.json");
    for (final String file : files) {
      final Document doc = Document.parse(file);
      final MongoCollection<Document> collection =
          db.getCollection(workflowCollectionPrefix + "roles");
      collection.insertOne(doc);
    }
  }

  /*
   * Migrate all Team relationships from ID to name
   */
  @ChangeSet(order = "4024", id = "4024", author = "Tyson Lawrie")
  public void migrateTeamRelationships(MongoDatabase db) throws IOException {
    LOGGER.info("Migrating Team Relationships");
    String teamsCollectionName = workflowCollectionPrefix + "teams";
    MongoCollection<Document> teamsCollection = db.getCollection(teamsCollectionName);

    String relationshipsCollectionName = workflowCollectionPrefix + "relationships";
    MongoCollection<Document> relationshipsCollection =
        db.getCollection(relationshipsCollectionName);

    final FindIterable<Document> teamRelationships = relationshipsCollection.find(eq("to", "TEAM"));
    for (final Document rel : teamRelationships) {
      Document team = (Document) teamsCollection
          .find(eq("_id", new ObjectId(rel.get("toRef").toString()))).first();
      if (team != null) {
        rel.replace("toRef", team.get("name"));
        relationshipsCollection.replaceOne(eq("_id", rel.getObjectId("_id")), rel);
      }
    }
  }
  
  /*
   * Adjust Extension setting to Integration Setting
   */
  @ChangeSet(order = "4025", id = "4025", author = "Tyson Lawrie")
  public void v4AdjustExtensionSettings(MongoDatabase db) throws IOException {
    LOGGER.info("Adjust Extension Settings");
    String collectionName = workflowCollectionPrefix + "settings";
    MongoCollection<Document> collection = db.getCollection(collectionName);

    Document setting =
        (Document) collection.find(eq("_id", new ObjectId("62a7bec0a6166d30aff64a5b"))).first();
    setting.replace("key", "integration");
    setting.replace("name", "Integration Configuration");
    List<Document> configs = (List<Document>) setting.get("config");
    Document ghAppIdConfig = new Document();
    ghAppIdConfig.put("key", "github.appId");
    ghAppIdConfig.put("description", "The GitHub App ID");
    ghAppIdConfig.put("label", "GitHub App ID");
    ghAppIdConfig.put("type", "text");
    ghAppIdConfig.put("value", "");
    ghAppIdConfig.put("readOnly", false);
    configs.add(ghAppIdConfig);
    Document ghPrivateKeyConfig = new Document();
    ghPrivateKeyConfig.put("key", "github.pem");
    ghPrivateKeyConfig.put("description", "Private key used to sign access token requests");
    ghPrivateKeyConfig.put("label", "GitHub Private Key");
    ghPrivateKeyConfig.put("type", "secured");
    ghPrivateKeyConfig.put("value", "");
    ghPrivateKeyConfig.put("readOnly", false);
    configs.add(ghPrivateKeyConfig);
    setting.replace("config", configs);
    collection.replaceOne(eq("_id", new ObjectId("62a7bec0a6166d30aff64a5b")),
        setting);
  }
  
  /*
   * Migrate Triggers
   */
  @ChangeSet(order = "4026", id = "4026", author = "Tyson Lawrie")
  public void v4MigrateWorkflowTriggers(MongoDatabase db) throws IOException {
    LOGGER.info("Adjust Extension Settings");

    String workflowsCollectionName = workflowCollectionPrefix + "workflows";
    MongoCollection<Document> workflowsCollection = db.getCollection(workflowsCollectionName);

    final FindIterable<Document> workflowsEntities = workflowsCollection.find();
    for (final Document workflowsEntity : workflowsEntities) {
      LOGGER.info("Migrating Triggers for WorkflowId: " + workflowsEntity.get("_id"));
      if (workflowsEntity.get("triggers") != null) {
        Document triggers = (Document) workflowsEntity.get("triggers");
        if (triggers.get("manual") != null) {
          Document manual = (Document) triggers.get("manual");
          Document migratedManual = new Document();
          migratedManual.put("enabled", manual.getBoolean("enable", false));
          migratedManual.put("conditions", new LinkedList<>());
          triggers.put("manual", migratedManual);
        }
        if (triggers.get("scheduler") != null) {
          Document scheduler = (Document) triggers.get("scheduler");
          Document migratedScheduler = new Document();
          migratedScheduler.put("enabled", scheduler.getBoolean("enable", false));
          migratedScheduler.put("conditions", new LinkedList<>());
          triggers.put("schedule", migratedScheduler);
          triggers.remove("scheduler");
        }
        if (triggers.get("webhook") != null) {
          Document webhook = (Document) triggers.get("webhook");
          Document migratedWebhook = new Document();
          migratedWebhook.put("enabled", webhook.getBoolean("enable", false));
          migratedWebhook.put("conditions", new LinkedList<>());
          triggers.put("webhook", migratedWebhook);
        }
        Document migratedEvent = new Document();
        migratedEvent.put("enabled", false);
        migratedEvent.put("conditions", new LinkedList<>());
        triggers.put("event", migratedEvent);
        workflowsEntity.replace("triggers", triggers);
        workflowsCollection.replaceOne(eq("_id", workflowsEntity.getObjectId("_id")),
            workflowsEntity);
      }
    }
  }
  
  /*
   * Load Integration Templates
   */
  @ChangeSet(order = "4027", id = "4027", author = "Tyson Lawrie")
  public void loadIntegrationTemplates(MongoDatabase db) throws IOException {
    LOGGER.info("Loading Integration Templates");
    String collectionName = workflowCollectionPrefix + "integration_templates";
    MongoCollection<Document> collection = db.getCollection(collectionName);
    if (collection == null) {
      db.createCollection(collectionName);
    }
    collection = db.getCollection(collectionName);
    final List<String> files = fileloadingService.loadFiles("flow/4027/*.json");
    for (final String file : files) {
      final Document doc = Document.parse(file);
      collection.insertOne(doc);
    }
  }
  
  /*
   * Load Integration Templates
   */
  @ChangeSet(order = "4028", id = "4028", author = "Tyson Lawrie")
  public void loadManualApprovalTaskTemplateRevision(MongoDatabase db) throws IOException {
    LOGGER.info("Loading Task Template Revision");
    String collectionName = workflowCollectionPrefix + "task_template_revisions";
    MongoCollection<Document> collection = db.getCollection(collectionName);
    if (collection == null) {
      db.createCollection(collectionName);
    }
    collection = db.getCollection(collectionName);
    final List<String> files = fileloadingService.loadFiles("flow/4028/*.json");
    for (final String file : files) {
      final Document doc = Document.parse(file);
      collection.insertOne(doc);
    }
  }
  
  /*
   * Create a unqiue index for a User
   */
  @ChangeSet(order = "4029", id = "4029", author = "Tyson Lawrie")
  public void v4CreateUserUniqueIndex(MongoDatabase db) throws IOException {
    LOGGER.info("Create Indexes");
    String collectionName = workflowCollectionPrefix + "users";
    MongoCollection<Document> collection = db.getCollection(collectionName);
    try {
      collection.createIndex(Indexes.ascending("email"), new IndexOptions().unique(true));
    } catch (Exception e) {

    }
  }
  
  /*
   * Convert TaskTemplate to parentRef
   */
  @ChangeSet(order = "4030", id = "4030", author = "Tyson Lawrie")
  public void v4ConvertTaskTemplateToParentRef(MongoDatabase db) throws IOException {
    LOGGER.info("Converting TaskTemplate Revision Parent References");
    String ttrCollectionName = workflowCollectionPrefix + "task_template_revisions";
    MongoCollection<Document> ttrCollection = db.getCollection(ttrCollectionName);
    String ttCollectionName = workflowCollectionPrefix + "task_templates";
    MongoCollection<Document> ttCollection = db.getCollection(ttCollectionName);
    final FindIterable<Document> ttrEntities = ttrCollection.find();
    for (final Document ttrEntity : ttrEntities) {
      Document ttEntity = (Document) ttCollection.find(eq("name", ttrEntity.get("parent"))).first();
      ttrEntity.put("parentRef", ttEntity.get("_id").toString());
      ttrEntity.remove("parent");
      ttrCollection.replaceOne(eq("_id", ttrEntity.getObjectId("_id")), ttrEntity);
    }
  }
  /*
   * Convert Relationships from Single Collection to separate collections
   */
  @ChangeSet(order = "4031", id = "4031", author = "Tyson Lawrie")
  public void v4ConvertRelationships(MongoDatabase db) throws IOException {
    LOGGER.info("Converting Relationships");
    //Original Collection
    String relCollectionName = workflowCollectionPrefix + "relationships";
    String relV1CollectionName = workflowCollectionPrefix + "relationships_v1";
    MongoCollection<Document> relCollection = db.getCollection(relCollectionName);
    //Rename new to 
    MongoNamespace v1RelNamespace = new MongoNamespace(db.getName(), relV1CollectionName);
    relCollection.renameCollection(v1RelNamespace);
    //Drop old collection
    MongoCollection<Document> tempCollection = db.getCollection(relCollectionName);
    tempCollection.drop();
    //New Collections
    db.createCollection(relCollectionName);
    MongoCollection<Document>relV2Collection = db.getCollection(relCollectionName);
    MongoCollection<Document>relV1Collection = db.getCollection(relV1CollectionName);
    
    // Loop through Users, Teams, Workflows, WorkflowRuns and create Nodes.    
    // Create Team Nodes
    String teamCollectionName = workflowCollectionPrefix + "teams";
    MongoCollection<Document> teamCollection = db.getCollection(teamCollectionName);
    final FindIterable<Document> teamEntities = teamCollection.find();
    for (final Document entity : teamEntities) {
      Document node = new Document();
      node.put("creationDate", new Date());
      node.put("type", "TEAM");
      node.put("data", new HashMap<>());
      node.put("connections", new ArrayList<>());
      node.put("ref", entity.getObjectId("_id").toString());
      node.put("slug", entity.get("name").toString());
      relV2Collection.insertOne(node);
    }
    
    // Create User Nodes
    String usersCollectionName = workflowCollectionPrefix + "users";
    MongoCollection<Document> usersCollection = db.getCollection(usersCollectionName);
    final FindIterable<Document> userEntities = usersCollection.find();
    for (final Document entity : userEntities) {
      Document node = new Document();
      node.put("creationDate", new Date());
      node.put("type", "USER");
      node.put("data", new HashMap<>());
      node.put("connections", new ArrayList<>());
      node.put("ref", entity.getObjectId("_id").toString());
      node.put("slug", entity.get("email").toString());
      relV2Collection.insertOne(node);
    }
    
    // Create TaskTemplate Nodes
    String ttCollectionName = workflowCollectionPrefix + "task_templates";
    MongoCollection<Document> ttCollection = db.getCollection(ttCollectionName);
    final FindIterable<Document> ttEntities = ttCollection.find();
    for (final Document entity : ttEntities) {
      Document node = new Document();
      node.put("creationDate", new Date());
      node.put("type", "TASK");
      node.put("data", new HashMap<>());
      node.put("connections", new ArrayList<>());
      node.put("ref", entity.getObjectId("_id").toString());
      node.put("slug", entity.get("name").toString());
      relV2Collection.insertOne(node);
    }
    
    // Create Workflow Nodes
    String wfCollectionName = workflowCollectionPrefix + "workflows";
    MongoCollection<Document> wfCollection = db.getCollection(wfCollectionName);
    final FindIterable<Document> wfEntities = wfCollection.find();
    for (final Document entity : wfEntities) {
      Document node = new Document();
      node.put("creationDate", new Date());
      node.put("type", "WORKFLOW");
      node.put("data", new HashMap<>());
      node.put("connections", new ArrayList<>());
      node.put("ref", entity.getObjectId("_id").toString());
      node.put("slug", entity.get("name").toString());
      relV2Collection.insertOne(node);
    }
    
    // Create WorkflowRun Nodes
    String wfRunCollectionName = workflowCollectionPrefix + "workflow_runs";
    MongoCollection<Document> wfRunCollection = db.getCollection(wfRunCollectionName);
    final FindIterable<Document> wfRunEntities = wfRunCollection.find();
    for (final Document entity : wfRunEntities) {
      Document node = new Document();
      node.put("creationDate", new Date());
      node.put("type", "WORKFLOWRUN");
      node.put("data", new HashMap<>());
      node.put("connections", new ArrayList<>());
      node.put("ref", entity.getObjectId("_id").toString());
      node.put("slug", ""); //WORKFLOWRUNS don't have a slug currently
      relV2Collection.insertOne(node);
    }
    
    // Create ApproverGroup Nodes
    String agCollectionName = workflowCollectionPrefix + "approver_groups";
    MongoCollection<Document> agCollection = db.getCollection(agCollectionName);
    final FindIterable<Document> agEntities = agCollection.find();
    for (final Document entity : agEntities) {
      Document node = new Document();
      node.put("creationDate", new Date());
      node.put("type", "APPROVERGROUP");
      node.put("data", new HashMap<>());
      node.put("connections", new ArrayList<>());
      node.put("ref", entity.getObjectId("_id").toString());
      node.put("slug", entity.get("name").toString());
      relV2Collection.insertOne(node);
    }
    
    // Loop through and match to the nodes and add paths
    // Assumes the nodes all exist
    final FindIterable<Document> relEntities = relV1Collection.find();
    for (final Document entity : relEntities) {
      if (entity.get("from").toString().equals("TASKTEMPLATE") && entity.get("to").toString().equals("GLOBAL")) {
        // Global Task Templates become GLOBALTASK  
        Document v2Node = relV2Collection.find(eq("slug", entity.get("fromRef"))).first();
        if (v2Node != null) {
          v2Node.put("type", "GLOBALTASK");
          relV2Collection.replaceOne(eq("_id", v2Node.getObjectId("_id")), v2Node);
        } else {
          LOGGER.error("Unable to find matching Task with slug: {}", entity.get("fromRef"));
        }
      } else {
        // Create Relationships to the Team
        String type = entity.get("from").toString();
        if (type.equals("TASKTEMPLATE")) {
          type = "TASK";
        }
        LOGGER.info("Attempting to find node of type: {}, and ref/slug: {}", type, entity.get("fromRef"));
        Document v2FromNode = relV2Collection.find(Filters.and(eq("type", type), Filters.or(eq("ref", entity.get("fromRef")), eq("slug", entity.get("fromRef"))))).first();
        Document v2ToNode = relV2Collection.find(Filters.and(eq("type", entity.get("to")), Filters.or(eq("ref", entity.get("toRef")), eq("slug", entity.get("toRef"))))).first();
        if (v2FromNode != null && v2ToNode != null) {
          Document connection = new Document();
          connection.put("creationDate", new Date());
          connection.put("label", entity.get("type"));
          connection.put("to", v2ToNode.getObjectId("_id"));
          connection.put("data", entity.get("data"));
          List<Document> connections = (List<Document>) v2FromNode.get("connections");
          connections.add(connection);
          v2FromNode.replace("connections", connections);
          relV2Collection.replaceOne(eq("_id", v2FromNode.getObjectId("_id")), v2FromNode);
        } else {
          LOGGER.error("Unable to find node of type: {}, and ref/slug: {}", type, entity.get("fromRef"));
        }
      }
    }
  }
  
  /*
   * Change TaskTemplate to Task
   */
  @ChangeSet(order = "4032", id = "4032", author = "Tyson Lawrie")
  public void v4ChangeTaskTemplateToTask(MongoDatabase db) throws IOException {
    LOGGER.info("Change TaskTemplate to Task");
    String ttrCollectionName = workflowCollectionPrefix + "task_template_revisions";
    MongoCollection<Document> ttrCollection = db.getCollection(ttrCollectionName);
    MongoNamespace taskRevisionNamespace = new MongoNamespace(db.getName(), workflowCollectionPrefix + "task_revisions");
    ttrCollection.renameCollection(taskRevisionNamespace);
    String ttCollectionName = workflowCollectionPrefix + "task_templates";
    MongoCollection<Document> ttCollection = db.getCollection(ttCollectionName);
    MongoNamespace taskNamespace = new MongoNamespace(db.getName(), workflowCollectionPrefix + "tasks");
    ttCollection.renameCollection(taskNamespace);
  }
  
  /*
   * Change templateRef to taskRef in TaskRuns
   */
  @ChangeSet(order = "4033", id = "4033", author = "Tyson Lawrie")
  public void v4ConvertTRTemplateRefToTaskRef(MongoDatabase db) throws IOException {
    LOGGER.info("Change WorkflowTask Ref Implementation");
    String trCollectionName = workflowCollectionPrefix + "task_runs";
    MongoCollection<Document> trCollection = db.getCollection(trCollectionName);
    String tskCollectionName = workflowCollectionPrefix + "tasks";
    MongoCollection<Document> tskCollection = db.getCollection(tskCollectionName);

    final FindIterable<Document> entities = trCollection.find();
    for (final Document entity : entities) {
      if (entity.get("templateRef") != null) {
        Document tskEntity =
            (Document) tskCollection.find(eq("name", entity.get("templateRef"))).first();
        if (tskEntity != null) {
          entity.put("taskRef", tskEntity.get("_id").toString());
          entity.remove("templateRef");
        } else {
          LOGGER.error("Unable to find task with name: {}", entity.get("templateRef"));
        }
      }
      if (entity.get("templateVersion") != null) {
        entity.put("taskVersion", entity.get("taskVersion"));
        entity.remove("templateVersion");
      }
      trCollection.replaceOne(eq("_id", entity.getObjectId("_id")), entity);
    }
  }
  
  /*
   * Change TaskTemplate to Task in Workflows
   */
  @ChangeSet(order = "4034", id = "4034", author = "Tyson Lawrie")
  public void v4ConvertWorkflowTaskRef(MongoDatabase db) throws IOException {
    LOGGER.info("Change WorkflowTask Ref Implementation");
    String wfrCollectionName = workflowCollectionPrefix + "workflow_revisions";
    MongoCollection<Document> wfrCollection = db.getCollection(wfrCollectionName);
    String tskCollectionName = workflowCollectionPrefix + "tasks";
    MongoCollection<Document> tskCollection = db.getCollection(tskCollectionName);

    final FindIterable<Document> entities = wfrCollection.find();
    for (final Document entity : entities) {
      List<Document> wfTasks = (List<Document>) entity.get("tasks");
      for (final Document wfTask : wfTasks) {
        if (wfTask.get("templateRef") != null && (!wfTask.get("name").toString().equals("start") || !wfTask.get("name").toString().equals("end"))) {
          Document tskEntity = (Document) tskCollection.find(eq("name", wfTask.get("templateRef"))).first();
          if (tskEntity != null) {
            wfTask.put("taskRef", tskEntity.get("_id").toString());
            wfTask.remove("templateRef");
          } else {
            LOGGER.error("Unable to find task with name: {}", wfTask.get("templateRef"));
          }
        }
        if (wfTask.get("templateVersion") != null) {
          wfTask.put("taskVersion", wfTask.get("taskVersion"));
          wfTask.remove("templateVersion");
        }
      }
      wfrCollection.replaceOne(eq("_id", entity.getObjectId("_id")), entity);
    }
  }
  
  /*
   * Change TaskTemplate to Task in WorkflowTemplate
   */
  @ChangeSet(order = "4035", id = "4035", author = "Tyson Lawrie")
  public void v4ConvertWorkflowTemplateTaskRef(MongoDatabase db) throws IOException {
    LOGGER.info("Change WorkflowTask Ref Implementation");
    String wftCollectionName = workflowCollectionPrefix + "workflow_templates";
    MongoCollection<Document> wftCollection = db.getCollection(wftCollectionName);
    String tskCollectionName = workflowCollectionPrefix + "tasks";
    MongoCollection<Document> tskCollection = db.getCollection(tskCollectionName);

    final FindIterable<Document> entities = wftCollection.find();
    for (final Document entity : entities) {
      List<Document> wfTasks = (List<Document>) entity.get("tasks");
      for (final Document wfTask : wfTasks) {
        if (wfTask.get("templateRef") != null && (!wfTask.get("name").toString().equals("start") || !wfTask.get("name").toString().equals("end"))) {
          Document tskEntity = (Document) tskCollection.find(eq("name", wfTask.get("templateRef"))).first();
          if (tskEntity != null) {
            wfTask.put("taskRef", tskEntity.get("_id").toString());
            wfTask.remove("templateRef");
          } else {
            LOGGER.error("Unable to find task with name: {}", wfTask.get("templateRef"));
          }
        }
        if (wfTask.get("templateVersion") != null) {
          wfTask.put("taskVersion", wfTask.get("taskVersion"));
          wfTask.remove("templateVersion");
        }
      }
      wftCollection.replaceOne(eq("_id", entity.getObjectId("_id")), entity);
    }
  }
  
  /*
   * Update Integration Template
   */
  @ChangeSet(order = "4036", id = "4036", author = "Tyson Lawrie")
  public void updateIntegrationTemplates(MongoDatabase db) throws IOException {
    LOGGER.info("Update Sleep Task Template");
    final List<String> taskTemplates =
        fileloadingService.loadFiles("flow/4036/*.json");
    for (final String template : taskTemplates) {
      final Document doc = Document.parse(template);
      final MongoCollection<Document> collection =
          db.getCollection(workflowCollectionPrefix + "integration_templates");
      collection.replaceOne(eq("name", doc.get("name")), doc);
    }
  }
  
  /*
   * Add GitHub App Name to Integration Setting
   */
  @ChangeSet(order = "4037", id = "4037", author = "Tyson Lawrie")
  public void v4AdjustIntegrationSettings(MongoDatabase db) throws IOException {
    LOGGER.info("Adjust Extension Settings");
    String collectionName = workflowCollectionPrefix + "settings";
    MongoCollection<Document> collection = db.getCollection(collectionName);

    Document setting =
        (Document) collection.find(eq("_id", new ObjectId("62a7bec0a6166d30aff64a5b"))).first();
    List<Document> configs = (List<Document>) setting.get("config");
    Document ghAppNameConfig = new Document();
    ghAppNameConfig.put("key", "github.appName");
    ghAppNameConfig.put("description", "The GitHub App Name");
    ghAppNameConfig.put("label", "GitHub App Name");
    ghAppNameConfig.put("type", "text");
    ghAppNameConfig.put("value", "");
    ghAppNameConfig.put("readOnly", false);
    configs.add(ghAppNameConfig);
    setting.replace("config", configs);
    collection.replaceOne(eq("_id", new ObjectId("62a7bec0a6166d30aff64a5b")),
        setting);
  }
  
  /*
   * Add Audit records for existing teams
   */
  @ChangeSet(order = "4038", id = "4038", author = "Tyson Lawrie")
  public void v4InsertTeamAuditRecords(MongoDatabase db) throws IOException {
    LOGGER.info("Add Team Audit Records");
    String auditCollectionName = workflowCollectionPrefix + "audit";
    MongoCollection<Document> auditCollection = db.getCollection(auditCollectionName);

    String teamsCollectionName = workflowCollectionPrefix + "teams";
    MongoCollection<Document> teamsCollection = db.getCollection(teamsCollectionName);
    
    Map<String, String> teamAuditIds = new HashMap<>();
    final FindIterable<Document> entities = teamsCollection.find();
    for (final Document entity : entities) {
      Document audit = new Document();
      audit.put("scope", "TEAM");
      audit.put("selfRef", entity.getObjectId("_id").toString());
      audit.put("selfName", entity.get("name").toString());
      audit.put("creationDate", entity.get("creationDate"));
      List<Document> events = new ArrayList<>();
      Document event = new Document();
      event.put("type", "created");
      event.put("date", entity.get("creationDate"));
      events.add(event);
      audit.put("events", events);
      Map<String, String> data = new HashMap<>();
      data.put("name", entity.get("name").toString());
      audit.put("data", data);
      InsertOneResult auditEntity = auditCollection.insertOne(audit);
      teamAuditIds.put(entity.get("name").toString(), auditEntity.getInsertedId().asObjectId().getValue().toString());
    }
   
    String wfCollectionName = workflowCollectionPrefix + "workflows";
    MongoCollection<Document> wfCollection = db.getCollection(wfCollectionName);
    
    final FindIterable<Document> wfEntities = wfCollection.find();
    for (final Document entity : wfEntities) {
      String teamSlug = this.relationshipTeamSlug(db, entity.getObjectId("_id").toString());
      if (teamSlug.isBlank()) {
        LOGGER.warn("Unable to find Team Slug for Workflow: {} ({})", entity.get("name"), entity.getObjectId("_id").toString());
      } else {
        LOGGER.info("Team Slug: {}", teamSlug);
        LOGGER.info("Team Audit ID: {}", teamAuditIds.get(teamSlug));
        Document audit = new Document();
        audit.put("scope", "WORKFLOW");
        audit.put("selfRef", entity.getObjectId("_id").toString());
        audit.put("parent", teamAuditIds.get(teamSlug));
        audit.put("creationDate", entity.get("creationDate"));
        List<Document> events = new ArrayList<>();
        Document event = new Document();
        event.put("type", "created");
        event.put("date", entity.get("creationDate"));
        events.add(event);
        audit.put("events", events);
        Map<String, String> data = new HashMap<>();
        data.put("name", entity.get("name").toString());
        audit.put("data", data);
        auditCollection.insertOne(audit);
      }
    }
  }
  
  private String relationshipTeamSlug(MongoDatabase db, String refOrSlug) {
    String relCollectionName = workflowCollectionPrefix + "relationships";
    MongoCollection<Document> relCollection = db.getCollection(relCollectionName);
    Bson matchStage = Aggregates.match(Filters.and(Filters.eq("type", "WORKFLOW"),
        Filters.or(Filters.eq("slug", refOrSlug), Filters.eq("ref", refOrSlug))));

    Bson graphLookupStage = Aggregates.graphLookup(relCollectionName, "$connections.to",
        "connections.to", "_id", "children",
        new GraphLookupOptions().restrictSearchWithMatch(Filters.eq("type", "TEAM")));

    List<Document> graph = relCollection.aggregate(Arrays.asList(matchStage, graphLookupStage))
        .into(new ArrayList<>());
    List<Document> children = graph.size() > 0 && graph.get(0) != null && graph.get(0).get("children") != null
        ? (List<Document>) graph.get(0).get("children")
        : new ArrayList<>();
    String slug =
        children.size() > 0 && children.get(0) != null && children.get(0).get("slug") != null
            ? (String) children.get(0).get("slug")
            : "";
    return slug;

  }

  /*
   * Adjust Team Quota settings
   */
  @ChangeSet(order = "4039", id = "4039", author = "Tyson Lawrie")
  public void v4AdjustTeamSettings(MongoDatabase db) throws IOException {
    LOGGER.info("Adjust Team Settings");
    String collectionName = workflowCollectionPrefix + "settings";
    MongoCollection<Document> collection = db.getCollection(collectionName);

    Document teamSetting =
        (Document) collection.find(eq("key", "teams")).first();
    teamSetting.put("description", "Define default team quotas which are referenced unless overridden on the Team.");
    teamSetting.put("name", "Team Quotas");
    List<Document> configs = (List<Document>) teamSetting.get("config");
    if (configs != null && !configs.isEmpty()) {
      for (final Document config : configs) {
        if (config.get("key").equals("max.team.concurrent.workflows")) {
          config.replace("key", "max.workflowrun.concurrent");
        } else if (config.get("key").equals("max.team.workflow.count")) {
          config.replace("key", "max.workflow.count");
        } else if (config.get("key").equals("max.team.workflow.execution.monthly")) {
          config.replace("key", "max.workflowrun.monthly");
          config.replace("label", "Max WorkflowRuns per month");
        } else if (config.get("key").equals("max.team.workflow.duration")) {
          config.replace("key", "max.workflowrun.duration");
          config.replace("label", "Max WorkflowRun Duration");
        } else if (config.get("key").equals("max.team.workflow.storage")) {
          config.replace("key", "max.workflow.storage");
          config.replace("description", "Maximum storage allowed per Workflow across runs (executions)");
          config.replace("label", "Max Workflow Storage");
        }
      }
    }
    Document wfRunStorage = new Document();
    wfRunStorage.put("key", "max.workflowrun.storage");
    wfRunStorage.put("description", "Maximum storage allowed per WorkflowRun (execution)");
    wfRunStorage.put("label", "Max WorkflowRun Storage");
    wfRunStorage.put("type", "text");
    wfRunStorage.put("value", "2Gi");
    wfRunStorage.put("readOnly", false);
    configs.add(wfRunStorage);
    teamSetting.replace("config", configs);
    collection.replaceOne(eq("key", "teams"), teamSetting);
  }

  /*
   * Adjust Feature settings
   */
  @ChangeSet(order = "4040", id = "4040", author = "Tyson Lawrie")
  public void v4AdjustFeatureSettings(MongoDatabase db) throws IOException {
    LOGGER.info("Adjust feature Settings");
    String collectionName = workflowCollectionPrefix + "settings";
    MongoCollection<Document> collection = db.getCollection(collectionName);

    Document setting =
        (Document) collection.find(eq("key", "features")).first();
    List<Document> configs = (List<Document>) setting.get("config");
    if (configs != null && !configs.isEmpty()) {
      for (final Document config : configs) {
        if (config.get("key").equals("workflowQuotas")) {
          config.replace("key", "teamQuotas");
          config.replace("label", "Team Quotas");
          config.replace("description", "Enforce Team level quotas");
        }
      }
    }
    setting.replace("config", configs);
    collection.replaceOne(eq("key", "features"), setting);
  }
}
