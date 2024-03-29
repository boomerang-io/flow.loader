package io.boomerang.migration.changesets;

import static com.mongodb.client.model.Filters.eq;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.bson.Document;
import org.bson.conversions.Bson;
import org.bson.types.ObjectId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.github.cloudyrock.mongock.ChangeLog;
import com.github.cloudyrock.mongock.ChangeSet;
import com.mongodb.BasicDBObject;
import com.mongodb.client.FindIterable;
import com.mongodb.client.ListIndexesIterable;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.IndexOptions;
import com.mongodb.client.model.Indexes;
import com.mongodb.client.model.Updates;
import io.boomerang.migration.FileLoadingService;
import io.boomerang.migration.SpringContextBridge;

@ChangeLog
public class FlowDatabaseChangeLog {

  private static FileLoadingService fileloadingService;

  private static String workflowCollectionPrefix;

  private static boolean mongoCosmosDBTTL;

  private final Logger logger = LoggerFactory.getLogger(FlowDatabaseChangeLog.class);

  public FlowDatabaseChangeLog() {
    fileloadingService = SpringContextBridge.services().getFileLoadingService();
    workflowCollectionPrefix = SpringContextBridge.services().getCollectionPrefix();
    mongoCosmosDBTTL= SpringContextBridge.services().getMongoCosmosDBTTL();
  }
  
  @ChangeSet(order = "001", id = "001", author = "Marcus Roy")
  public void initialSetup(MongoDatabase db) throws IOException {

    logger.info("Running change log: #1 - Creating Collections for Boomerang Flow");

    MongoCollection<Document> collection =
        db.getCollection(workflowCollectionPrefix + "task_templates");
    if (collection == null) {
      db.createCollection(workflowCollectionPrefix + "task_templates");
    }

    collection = db.getCollection(workflowCollectionPrefix + "teams");
    if (collection == null) {
      db.createCollection(workflowCollectionPrefix + "teams");
    }

    collection = db.getCollection(workflowCollectionPrefix + "workflows");
    if (collection == null) {
      db.createCollection(workflowCollectionPrefix + "workflows");
    }

    collection = db.getCollection(workflowCollectionPrefix + "workflows_activity");
    if (collection == null) {
      db.createCollection(workflowCollectionPrefix + "workflows_activity");
    }

    collection = db.getCollection(workflowCollectionPrefix + "workflows_activity_task");
    if (collection == null) {
      db.createCollection(workflowCollectionPrefix + "workflows_activity_task");
    }

    collection = db.getCollection(workflowCollectionPrefix + "workflows_revisions");
    if (collection == null) {
      db.createCollection(workflowCollectionPrefix + "workflows_revisions");
    }
  }

  @ChangeSet(order = "002", id = "002", author = "Marcus Roy")
  public void loadInTemplates(MongoDatabase db) throws IOException {

    logger.info("Running change log: #2 - Loading in flow templates");

    final List<String> files = fileloadingService.loadFiles("flow/001/flow_task_templates/*.json");
    for (final String fileContents : files) {
      final Document doc = Document.parse(fileContents);
      final MongoCollection<Document> collection =
          db.getCollection(workflowCollectionPrefix + "task_templates");
      collection.insertOne(doc);
    }
  }

  @ChangeSet(order = "003", id = "003", author = "Adrienne Hudson")
  public void updateTaskTemplates(MongoDatabase db) throws IOException {

    db.getCollection(workflowCollectionPrefix + "task_templates")
        .deleteOne(eq("name", "Execute HTTP Call"));

    final List<String> files = fileloadingService.loadFiles("flow/002/flow_task_templates/*.json");
    for (final String fileContents : files) {
      final Document doc = Document.parse(fileContents);
      final MongoCollection<Document> collection =
          db.getCollection(workflowCollectionPrefix + "task_templates");
      collection.insertOne(doc);

    }
  }

  @ChangeSet(order = "004", id = "004", author = "Adrienne Hudson")
  public void updateTaskTemplate(MongoDatabase db) throws IOException {

    db.getCollection(workflowCollectionPrefix + "task_templates")
        .deleteOne(eq("name", "Send Slack Message"));

    final List<String> files = fileloadingService.loadFiles("flow/003/flow_task_templates/*.json");
    for (final String fileContents : files) {
      final Document doc = Document.parse(fileContents);
      final MongoCollection<Document> collection =
          db.getCollection(workflowCollectionPrefix + "task_templates");
      collection.insertOne(doc);

    }
  }

  @ChangeSet(order = "005", id = "005", author = "Adrienne Hudson")
  public void updateFlowTemplates(MongoDatabase db) throws IOException {
    final MongoCollection<Document> modesCollection =
        db.getCollection(workflowCollectionPrefix + "task_templates");

    Bson query1 = Filters.eq("name", "Get Incidents");
    Bson update = Updates.set("nodetype", "custom");
    modesCollection.findOneAndUpdate(query1, update);

    Bson query2 = Filters.eq("name", "Update Incidents");
    modesCollection.findOneAndUpdate(query2, update);

    Bson query3 = Filters.eq("name", "Execute HTTP Call");
    modesCollection.findOneAndUpdate(query3, update);

  }

  @ChangeSet(order = "006", id = "006", author = "Adrienne Hudson")
  public void addTaskTemplates(MongoDatabase db) throws IOException {

    final List<String> files = fileloadingService.loadFiles("flow/006/flow_task_templates/*.json");
    for (final String fileContents : files) {
      final Document doc = Document.parse(fileContents);
      final MongoCollection<Document> collection =
          db.getCollection(workflowCollectionPrefix + "task_templates");
      collection.insertOne(doc);

    }
  }

  @ChangeSet(order = "007", id = "007", author = "Adrienne Hudson")
  public void addPlatformNotificationTemplate(MongoDatabase db) throws IOException {

    final List<String> files = fileloadingService.loadFiles("flow/007/flow_task_templates/*.json");
    for (final String fileContents : files) {
      final Document doc = Document.parse(fileContents);
      final MongoCollection<Document> collection =
          db.getCollection(workflowCollectionPrefix + "task_templates");
      collection.insertOne(doc);

    }
  }

  @ChangeSet(order = "008", id = "008", author = "Adrienne Hudson")
  public void updateFlowTaskTemplates(MongoDatabase db) throws IOException {

    db.getCollection(workflowCollectionPrefix + "task_templates")
        .deleteOne(eq("name", "Make Repositories Private"));
    db.getCollection(workflowCollectionPrefix + "task_templates")
        .deleteOne(eq("name", "Find Public Repositories in Org"));
    db.getCollection(workflowCollectionPrefix + "task_templates")
        .deleteOne(eq("name", "Update Incidents"));
    db.getCollection(workflowCollectionPrefix + "task_templates")
        .deleteOne(eq("name", "Get Incidents"));

    final List<String> files = fileloadingService.loadFiles("flow/008/flow_task_templates/*.json");
    for (final String fileContents : files) {
      final Document doc = Document.parse(fileContents);
      final MongoCollection<Document> collection =
          db.getCollection(workflowCollectionPrefix + "task_templates");
      collection.insertOne(doc);

    }
  }

  @ChangeSet(order = "009", id = "009", author = "Adrienne Hudson")
  public void updateFlowTaskTemplateOptions(MongoDatabase db) throws IOException {

    final MongoCollection<Document> flowTaskTemplateCollection =
        db.getCollection(workflowCollectionPrefix + "task_templates");
    final FindIterable<Document> flowTaskTemplates = flowTaskTemplateCollection.find();
    for (final Document flowTaskTemplate : flowTaskTemplates) {

      List<Document> configs = (List<Document>) flowTaskTemplate.get("config");
      if (configs != null) {
        for (Document config : configs) {

          List<String> options = (List<String>) config.get("options");
          if (options != null) {

            List<Document> newOptions = new ArrayList<>();
            for (String option : options) {
              Document newOption = new Document();
              newOption.put("key", option);
              newOption.put("value", option);
              newOptions.add(newOption);
            }

            config.remove("options");
            config.put("options", newOptions);


            flowTaskTemplateCollection.replaceOne(eq("_id", flowTaskTemplate.getObjectId("_id")),
                flowTaskTemplate);

          }
        }
      }
    }
  }

  @ChangeSet(order = "010", id = "010", author = "Adrienne Hudson")
  public void migrateFlowTaskTemplateDescription(MongoDatabase db) throws IOException {

    final MongoCollection<Document> flowTaskTemplateCollection =
        db.getCollection(workflowCollectionPrefix + "task_templates");
    final FindIterable<Document> flowTaskTemplates = flowTaskTemplateCollection.find();
    for (final Document flowTaskTemplate : flowTaskTemplates) {

      List<Document> configs = (List<Document>) flowTaskTemplate.get("config");
      if (configs != null) {
        for (Document config : configs) {
          String description = (String) config.get("description");

          if (description != null) {
            if (config.get("type").equals("textarea") || config.get("type").equals("text")
                || config.get("type").equals("select") || config.get("type").equals("multiselect")
                || config.get("type").equals("filter")) {

              config.put("placeholder", description);
              config.put("description", "");
            } else {
              config.put("helpertext", description);
              config.put("description", "");
            }

            flowTaskTemplateCollection.replaceOne(eq("_id", flowTaskTemplate.getObjectId("_id")),
                flowTaskTemplate);

          }
        }
      }
    }
  }

  @ChangeSet(order = "011", id = "011", author = "Adrienne Hudson")
  public void renameEnableIAMIntegration(MongoDatabase db) throws IOException {

    final MongoCollection<Document> flowWorkflowsCollection =
        db.getCollection(workflowCollectionPrefix + "workflows");
    final FindIterable<Document> flowWorkflows = flowWorkflowsCollection.find();
    for (final Document flowWorkflow : flowWorkflows) {

      Boolean enableIAMIntegration = (Boolean) flowWorkflow.get("enableIAMIntegration");

      if (enableIAMIntegration != null) {
        flowWorkflow.put("enableACCIntegration", enableIAMIntegration);
        flowWorkflow.remove("enableIAMIntegration");

        flowWorkflowsCollection.replaceOne(eq("_id", flowWorkflow.getObjectId("_id")),
            flowWorkflow);
      }

    }
  }

  @ChangeSet(order = "012", id = "012", author = "Adrienne Hudson")
  public void updateCustomNodetype(MongoDatabase db) throws IOException {
    final MongoCollection<Document> flowTaskTemplateCollection =
        db.getCollection(workflowCollectionPrefix + "task_templates");

    final FindIterable<Document> flowTemplates = flowTaskTemplateCollection.find();
    for (final Document flowTemplate : flowTemplates) {
      String nodeType = (String) flowTemplate.get("nodetype");
      if (nodeType != null && nodeType.equals("custom")) {
        flowTemplate.put("nodetype", "templateTask");

        flowTaskTemplateCollection.replaceOne(eq("_id", flowTemplate.getObjectId("_id")),
            flowTemplate);

      }
    }
  }

  @ChangeSet(order = "013", id = "013", author = "Adrienne Hudson")
  public void updateFlowTemplateCategories(MongoDatabase db) throws IOException {
    final MongoCollection<Document> templateCollection =
        db.getCollection(workflowCollectionPrefix + "task_templates");

    Bson update1 = Updates.set("category", "workflow");
    Bson update2 = Updates.set("category", "artifactory");
    Bson update3 = Updates.set("category", "Github");

    Bson query1 = Filters.eq("name", "Switch");
    templateCollection.findOneAndUpdate(query1, update1);

    Bson query2 = Filters.eq("name", "Artifactory File Download");
    templateCollection.findOneAndUpdate(query2, update2);

    Bson query3 = Filters.eq("name", "Artifactory File Upload");
    templateCollection.findOneAndUpdate(query3, update2);

    Bson query4 = Filters.eq("name", "Find Public Repositories in Org");
    templateCollection.findOneAndUpdate(query4, update3);

    Bson query5 = Filters.eq("name", "Make Repositories Private");
    templateCollection.findOneAndUpdate(query5, update3);
  }

  @ChangeSet(order = "014", id = "014", author = "Adrienne Hudson")
  public void updateEmailFlowTemplateName(MongoDatabase db) throws IOException {
    final MongoCollection<Document> templateCollection =
        db.getCollection(workflowCollectionPrefix + "task_templates");

    Bson update1 = Updates.set("name", "Send Platform Email");

    Bson query1 = Filters.eq("name", "Send Email");
    templateCollection.findOneAndUpdate(query1, update1);
  }

  @ChangeSet(order = "015", id = "015", author = "Adrienne Hudson")
  public void addTaskTemplate(MongoDatabase db) throws IOException {

    final List<String> files = fileloadingService.loadFiles("flow/009/flow_task_templates/*.json");
    for (final String fileContents : files) {
      final Document doc = Document.parse(fileContents);
      final MongoCollection<Document> collection =
          db.getCollection(workflowCollectionPrefix + "task_templates");
      collection.insertOne(doc);

    }
  }

  @ChangeSet(order = "016", id = "016", author = "Marcus Roy")
  public void updateTemplates(MongoDatabase db) throws IOException {

    BasicDBObject document = new BasicDBObject();
    final MongoCollection<Document> collection =
        db.getCollection(workflowCollectionPrefix + "task_templates");
    collection.deleteMany(document);

    final List<String> files = fileloadingService.loadFiles("flow/016/flow_task_templates/*.json");
    for (final String fileContents : files) {
      final Document doc = Document.parse(fileContents);

      collection.insertOne(doc);
    }
  }

  @ChangeSet(order = "017", id = "017", author = "Adrienne Hudson")
  public void flowTaskTemplateUpdate(MongoDatabase db) throws IOException {

    db.getCollection(workflowCollectionPrefix + "task_templates")
        .deleteOne(eq("name", "Execute Shell"));
    db.getCollection(workflowCollectionPrefix + "task_templates").deleteOne(eq("name", "Switch"));
    db.getCollection(workflowCollectionPrefix + "task_templates")
        .deleteOne(eq("name", "Send Rick Slack Message"));
    db.getCollection(workflowCollectionPrefix + "task_templates")
        .deleteOne(eq("name", "Send Simple Slack Message"));

    final List<String> files = fileloadingService.loadFiles("flow/017/flow_task_templates/*.json");
    for (final String fileContents : files) {
      final Document doc = Document.parse(fileContents);
      final MongoCollection<Document> collection =
          db.getCollection(workflowCollectionPrefix + "task_templates");
      collection.insertOne(doc);

    }
  }

  @ChangeSet(order = "018", id = "018", author = "Adrienne Hudson")
  public void addNewTaskTemplates(MongoDatabase db) throws IOException {

    final List<String> files = fileloadingService.loadFiles("flow/018/flow_task_templates/*.json");
    for (final String fileContents : files) {
      final Document doc = Document.parse(fileContents);
      final MongoCollection<Document> collection =
          db.getCollection(workflowCollectionPrefix + "task_templates");
      collection.insertOne(doc);

    }
  }

  @ChangeSet(order = "019", id = "019", author = "Adrienne Hudson")
  public void addNewTaskTemplate(MongoDatabase db) throws IOException {

    final List<String> files = fileloadingService.loadFiles("flow/019/flow_task_templates/*.json");
    for (final String fileContents : files) {
      final Document doc = Document.parse(fileContents);
      final MongoCollection<Document> collection =
          db.getCollection(workflowCollectionPrefix + "task_templates");
      collection.insertOne(doc);

    }
  }

  @ChangeSet(order = "020", id = "020", author = "Adrienne Hudson")
  public void updateFlowTaskTemplate(MongoDatabase db) throws IOException {

    db.getCollection(workflowCollectionPrefix + "task_templates")
        .deleteOne(eq("name", "Send Platform Email"));
    db.getCollection(workflowCollectionPrefix + "task_templates")
        .deleteOne(eq("name", "Send Slack Log Message"));


    final List<String> files = fileloadingService.loadFiles("flow/020/flow_task_templates/*.json");
    for (final String fileContents : files) {
      final Document doc = Document.parse(fileContents);
      final MongoCollection<Document> collection =
          db.getCollection(workflowCollectionPrefix + "task_templates");
      collection.insertOne(doc);

    }
  }

  @ChangeSet(order = "021", id = "021", author = "Adrienne Hudson")
  public void addFlowTaskTemplate(MongoDatabase db) throws IOException {

    final List<String> files = fileloadingService.loadFiles("flow/021/flow_task_templates/*.json");
    for (final String fileContents : files) {
      final Document doc = Document.parse(fileContents);
      final MongoCollection<Document> collection =
          db.getCollection(workflowCollectionPrefix + "task_templates");
      collection.insertOne(doc);

    }
  }

  @ChangeSet(order = "023", id = "023", author = "Adrienne Hudson")
  public void taskTemplateUpdate(MongoDatabase db) throws IOException {

    db.getCollection(workflowCollectionPrefix + "task_templates")
        .deleteOne(eq("name", "Execute HTTP Call"));
    db.getCollection(workflowCollectionPrefix + "task_templates")
        .deleteOne(eq("name", "Artifactory File Upload"));


    final List<String> files = fileloadingService.loadFiles("flow/023/flow_task_templates/*.json");
    for (final String fileContents : files) {
      final Document doc = Document.parse(fileContents);
      final MongoCollection<Document> collection =
          db.getCollection(workflowCollectionPrefix + "task_templates");
      collection.insertOne(doc);

    }
  }

  @ChangeSet(order = "024", id = "024", author = "Adrienne Hudson")
  public void addTwilloFlowTaskTemplate(MongoDatabase db) throws IOException {

    final List<String> files = fileloadingService.loadFiles("flow/024/flow_task_templates/*.json");
    for (final String fileContents : files) {
      final Document doc = Document.parse(fileContents);
      final MongoCollection<Document> collection =
          db.getCollection(workflowCollectionPrefix + "task_templates");
      collection.insertOne(doc);

    }
  }


  @ChangeSet(order = "025", id = "025", author = "Adrienne Hudson")
  public void verifyFlowTaskTemplates(MongoDatabase db) throws IOException {
    final MongoCollection<Document> flowTaskTemplateCollection =
        db.getCollection(workflowCollectionPrefix + "task_templates");

    final FindIterable<Document> flowTemplates = flowTaskTemplateCollection.find();
    for (final Document flowTemplate : flowTemplates) {
      flowTemplate.put("verified", true);
      flowTaskTemplateCollection.replaceOne(eq("_id", flowTemplate.getObjectId("_id")),
          flowTemplate);
    }
  }

  @ChangeSet(order = "026", id = "026", author = "Adrienne Hudson")
  public void createFlowSettings(MongoDatabase db) throws IOException {
    db.createCollection(workflowCollectionPrefix + "settings");

    final List<String> files = fileloadingService.loadFiles("flow/026/flow_settings/*.json");
    for (final String fileContents : files) {
      final Document doc = Document.parse(fileContents);
      final MongoCollection<Document> collection =
          db.getCollection(workflowCollectionPrefix + "settings");
      collection.insertOne(doc);
    }
  }

  @ChangeSet(order = "027", id = "027", author = "Adrienne Hudson")
  public void updateFlowSetting(MongoDatabase db) throws IOException {

    db.getCollection(workflowCollectionPrefix + "settings").deleteOne(eq("name", "Workers"));


    final List<String> files = fileloadingService.loadFiles("flow/027/flow_settings/*.json");
    for (final String fileContents : files) {
      final Document doc = Document.parse(fileContents);
      final MongoCollection<Document> collection =
          db.getCollection(workflowCollectionPrefix + "settings");
      collection.insertOne(doc);

    }
  }

  @ChangeSet(order = "028", id = "028", author = "Adrienne Hudson")
  public void taskTemplateUpdatrs(MongoDatabase db) throws IOException {

    final MongoCollection<Document> collection =
        db.getCollection(workflowCollectionPrefix + "task_templates");
    collection.deleteOne(eq("name", "Execute HTTP Call"));
    collection.deleteOne(eq("name", "Artifactory File Upload"));
    collection.deleteOne(eq("name", "Send Twilio SMS"));


    final List<String> files = fileloadingService.loadFiles("flow/028/flow_task_templates/*.json");
    for (final String fileContents : files) {
      final Document doc = Document.parse(fileContents);

      collection.insertOne(doc);
    }
  }

  @ChangeSet(order = "029", id = "029", author = "Adrienne Hudson")
  public void taskTemplateUpdates(MongoDatabase db) throws IOException {

    final MongoCollection<Document> collection =
        db.getCollection(workflowCollectionPrefix + "task_templates");
    collection.deleteOne(eq("name", "Execute Shell"));
    collection.deleteOne(eq("name", "Find Issues and Label"));

    final List<String> files = fileloadingService.loadFiles("flow/029/flow_task_templates/*.json");
    for (final String fileContents : files) {
      final Document doc = Document.parse(fileContents);

      collection.insertOne(doc);
    }
  }

  @ChangeSet(order = "030", id = "030", author = "Adrienne Hudson")
  public void templatesUpdate(MongoDatabase db) throws IOException {
    BasicDBObject document = new BasicDBObject();
    final MongoCollection<Document> collection =
        db.getCollection(workflowCollectionPrefix + "task_templates");
    collection.deleteMany(document);

    final List<String> files = fileloadingService.loadFiles("flow/030/flow_task_templates/*.json");
    for (final String fileContents : files) {
      final Document doc = Document.parse(fileContents);

      collection.insertOne(doc);
    }
  }

  @ChangeSet(order = "031", id = "031", author = "Dylan Landry")
  public void updateFlowSettingEnableTasks(MongoDatabase db) throws IOException {
    MongoCollection<Document> collection = db.getCollection(workflowCollectionPrefix + "settings");
    Document workers = collection.find(eq("name", "Workers")).first();
    List<Document> config = (List<Document>) workers.get("config");

    Document newConfig = new Document();
    newConfig.put("description", "When enabled, verified tasks can be edited in the task manager");
    newConfig.put("key", "enable.tasks");
    newConfig.put("label", "Enable Verified Tasks to be edited");
    newConfig.put("type", "boolean");
    newConfig.put("value", "false");

    config.add(newConfig);

    workers.put("config", config);
    collection.replaceOne(eq("name", "Workers"), workers);
  }

  @ChangeSet(order = "032", id = "032", author = "Adrienne Hudson")
  public void activityUpdate(MongoDatabase db) throws IOException {
    final MongoCollection<Document> collection =
        db.getCollection(workflowCollectionPrefix + "workflows_activity");

    final FindIterable<Document> activities = collection.find();
    for (final Document activity : activities) {
      if (activity.get("trigger").equals("cron")) {
        activity.put("trigger", "scheduler");
        collection.replaceOne(eq("_id", activity.getObjectId("_id")), activity);
      }
    }

  }

  @ChangeSet(order = "033", id = "033", author = "Adrienne Hudson")
  public void flowTaskTemplateUpdates(MongoDatabase db) throws IOException {

    final MongoCollection<Document> collection =
        db.getCollection(workflowCollectionPrefix + "task_templates");
    collection.deleteOne(eq("name", "Run Custom Task"));
    collection.deleteOne(eq("name", "Send Platform Notification"));
    collection.deleteOne(eq("name", "Send Platform Email"));


    final List<String> files = fileloadingService.loadFiles("flow/033/flow_task_templates/*.json");
    for (final String fileContents : files) {
      final Document doc = Document.parse(fileContents);

      collection.insertOne(doc);
    }
  }

  @ChangeSet(order = "034", id = "034", author = "Marcus Roy")
  public void addManualTaskTemplate(MongoDatabase db) throws IOException {

    final MongoCollection<Document> collection =
        db.getCollection(workflowCollectionPrefix + "task_templates");

    final List<String> files = fileloadingService.loadFiles("flow/034/flow_task_templates/*.json");
    for (final String fileContents : files) {
      final Document doc = Document.parse(fileContents);
      collection.insertOne(doc);
    }
  }

  @ChangeSet(order = "035", id = "035", author = "Dylan Landry")
  public void setScopeForWorkflows(MongoDatabase db) throws IOException {

    final MongoCollection<Document> collection =
        db.getCollection(workflowCollectionPrefix + "workflows");
    final FindIterable<Document> workflows = collection.find();

    for (final Document workflow : workflows) {
      if (workflow.get("scope") == null && workflow.get("flowTeamId") != null) {
        workflow.put("scope", "team");
      } else if (workflow.get("flowTeamId") == null) {
        workflow.put("scope", "system");
      }
      collection.replaceOne(eq("_id", workflow.getObjectId("_id")), workflow);
    }
  }


  @ChangeSet(order = "036", id = "036", author = "Adrienne Hudson")
  public void addingTaskTemplate(MongoDatabase db) throws IOException {

    final MongoCollection<Document> collection =
        db.getCollection(workflowCollectionPrefix + "task_templates");
    collection.deleteOne(eq("name", "Send Custom Slack Message"));
    collection.deleteOne(eq("name", "Send Simple Slack Message"));
    collection.deleteOne(eq("name", "Send Slack Message with File Contents"));
    collection.deleteOne(eq("name", "Slack User Look Up"));
    collection.deleteOne(eq("name", "Upload Slack File with Message"));
    collection.deleteOne(eq("name", "Run Custom Task"));

    final List<String> files = fileloadingService.loadFiles("flow/036/flow_task_templates/*.json");
    for (final String fileContents : files) {
      final Document doc = Document.parse(fileContents);
      collection.insertOne(doc);
    }
  }

  @ChangeSet(order = "037", id = "037", author = "Adrienne Hudson")
  public void updateFlowSettingWorkerImage(MongoDatabase db) throws IOException {
    MongoCollection<Document> collection = db.getCollection(workflowCollectionPrefix + "settings");
    Document workers = collection.find(eq("name", "Workers")).first();
    List<Document> configs = (List<Document>) workers.get("config");

    for (Document config : configs) {
      if (config.get("key").equals("worker.image")) {
        config.put("value", "boomerangio/worker-flow:2.5.7");
      }
    }

    workers.put("config", configs);
    collection.replaceOne(eq("name", "Workers"), workers);
  }

  @ChangeSet(order = "038", id = "038", author = "Marcus Roy")
  public void createLockCollection(MongoDatabase db) throws IOException {
    String collectionName = workflowCollectionPrefix + "tasks_locks";
    db.createCollection(collectionName);
    final MongoCollection<Document> collection = db.getCollection(collectionName);
    if (mongoCosmosDBTTL) {
      collection.createIndex(Indexes.ascending("_ts"),
          new IndexOptions().expireAfter(120L, TimeUnit.SECONDS));
    } else {
      collection.createIndex(Indexes.ascending("expireAt"),
          new IndexOptions().expireAfter(0L, TimeUnit.MILLISECONDS));
    }
  }

  @ChangeSet(order = "039", id = "039", author = "Adrienne Hudson")
  public void updateFlowSettingDefaultWorkerImage(MongoDatabase db) throws IOException {
    MongoCollection<Document> collection = db.getCollection(workflowCollectionPrefix + "settings");
    Document workers = collection.find(eq("name", "Workers")).first();
    List<Document> configs = (List<Document>) workers.get("config");

    for (Document config : configs) {
      if (config.get("key").equals("worker.image")) {
        config.put("value", "boomerangio/worker-flow:2.5.9");
      }
    }

    workers.put("config", configs);
    collection.replaceOne(eq("name", "Workers"), workers);
  }

  @ChangeSet(order = "040", id = "040", author = "Adrienne Hudson")
  public void updateDefaultWorkerImage(MongoDatabase db) throws IOException {
    MongoCollection<Document> collection = db.getCollection(workflowCollectionPrefix + "settings");
    Document workers = collection.find(eq("key", "controller")).first();
    List<Document> configs = (List<Document>) workers.get("config");

    for (Document config : configs) {
      if (config.get("key").equals("worker.image")) {
        config.put("value", "boomerangio/worker-flow:2.5.21");
      }
    }

    workers.put("config", configs);
    collection.replaceOne(eq("key", "controller"), workers);
  }

  @ChangeSet(order = "041", id = "041", author = "Adrienne Hudson")
  public void updateTemplate(MongoDatabase db) throws IOException {

    final MongoCollection<Document> collection =
        db.getCollection(workflowCollectionPrefix + "task_templates");
    collection.deleteOne(eq("name", "Create File"));


    final List<String> files = fileloadingService.loadFiles("flow/041/flow_task_templates/*.json");
    for (final String fileContents : files) {
      final Document doc = Document.parse(fileContents);
      collection.insertOne(doc);
    }
  }

  @ChangeSet(order = "042", id = "042", author = "Adrienne Hudson")
  public void updateSetting(MongoDatabase db) throws IOException {

    MongoCollection<Document> collection = db.getCollection(workflowCollectionPrefix + "settings");
    Document controller = collection.find(eq("key", "controller")).first();
    controller.put("name", "Task Configuration");
    controller.put("description", "The task and underlying task execution configuration.");

    List<Document> configs = (List<Document>) controller.get("config");
    for (Document config : configs) {
      if (config.get("key").equals("job.deletion.policy")) {
        config.put("description", "Deletion Policy");
        config.put("label", "Defines the completion state that will lead to worker removal");
      }
    }
    controller.put("config", configs);
    collection.replaceOne(eq("key", "controller"), controller);

  }

  @ChangeSet(order = "043", id = "043", author = "Adrienne Hudson")
  public void addFlowSettings(MongoDatabase db) throws IOException {
    MongoCollection<Document> collection = db.getCollection(workflowCollectionPrefix + "settings");

    final List<String> files = fileloadingService.loadFiles("flow/043/flow_settings/*.json");
    for (final String fileContents : files) {
      final Document doc = Document.parse(fileContents);
      collection.insertOne(doc);
    }
  }

  @ChangeSet(order = "044", id = "044", author = "Adrienne Hudson")
  public void updatingTaskTemplates(MongoDatabase db) throws IOException {

    final MongoCollection<Document> collection =
        db.getCollection(workflowCollectionPrefix + "task_templates");

    final List<String> files = fileloadingService.loadFiles("flow/044/flow_task_templates/*.json");
    for (final String fileContents : files) {
      final Document doc = Document.parse(fileContents);
      collection.findOneAndDelete(eq("_id", doc.getObjectId("_id")));
      collection.insertOne(doc);
    }
  }

  @ChangeSet(order = "045", id = "045", author = "Adrienne Hudson")
  public void updateCollectons(MongoDatabase db) throws IOException {

    MongoCollection<Document> collection =
        db.getCollection(workflowCollectionPrefix + "task_templates");

    final List<String> files = fileloadingService.loadFiles("flow/045/flow_task_templates/*.json");
    for (final String fileContents : files) {
      final Document doc = Document.parse(fileContents);
      collection.findOneAndDelete(eq("_id", doc.getObjectId("_id")));
      collection.insertOne(doc);
    }

    collection = db.getCollection(workflowCollectionPrefix + "settings");
    Document workers = collection.find(eq("key", "controller")).first();
    List<Document> configs = (List<Document>) workers.get("config");

    for (Document config : configs) {
      if (config.get("key").equals("worker.image")) {
        config.put("value", "boomerangio/worker-flow:2.5.28");
      }
    }

    workers.put("config", configs);
    collection.replaceOne(eq("key", "controller"), workers);
  }

  @ChangeSet(order = "046", id = "046", author = "Adrienne Hudson")
  public void addTemplate(MongoDatabase db) throws IOException {

    final List<String> files = fileloadingService.loadFiles("flow/046/flow_task_templates/*.json");
    for (final String fileContents : files) {
      final Document doc = Document.parse(fileContents);
      final MongoCollection<Document> collection =
          db.getCollection(workflowCollectionPrefix + "task_templates");
      collection.insertOne(doc);
    }
  }

  @ChangeSet(order = "047", id = "047", author = "Adrienne Hudson")
  public void updatingShellTaskTemplate(MongoDatabase db) throws IOException {

    final List<String> files = fileloadingService.loadFiles("flow/047/flow_task_templates/*.json");
    for (final String fileContents : files) {
      final Document doc = Document.parse(fileContents);
      final MongoCollection<Document> collection =
          db.getCollection(workflowCollectionPrefix + "task_templates");
      collection.findOneAndDelete(eq("_id", doc.getObjectId("_id")));
      collection.insertOne(doc);
    }
  }

  @ChangeSet(order = "048", id = "048", author = "Adrienne Hudson")
  public void updatingTemplates(MongoDatabase db) throws IOException {

    MongoCollection<Document> collection =
        db.getCollection(workflowCollectionPrefix + "task_templates");

    final List<String> files = fileloadingService.loadFiles("flow/048/flow_task_templates/*.json");
    for (final String fileContents : files) {
      final Document doc = Document.parse(fileContents);
      collection.findOneAndDelete(eq("_id", doc.getObjectId("_id")));
      collection.insertOne(doc);
    }

    collection = db.getCollection(workflowCollectionPrefix + "settings");
    Document workers = collection.find(eq("key", "controller")).first();
    List<Document> configs = (List<Document>) workers.get("config");

    for (Document config : configs) {
      if (config.get("key").equals("worker.image")) {
        config.put("value", "boomerangio/worker-flow:2.5.44");
      }
    }

    workers.put("config", configs);
    collection.replaceOne(eq("key", "controller"), workers);
  }

  @ChangeSet(order = "049", id = "049", author = "Adrienne Hudson")
  public void updateTaskTemplatesAndWorkerImage(MongoDatabase db) throws IOException {

    MongoCollection<Document> collection =
        db.getCollection(workflowCollectionPrefix + "task_templates");

    final List<String> files = fileloadingService.loadFiles("flow/049/flow_task_templates/*.json");
    for (final String fileContents : files) {
      final Document doc = Document.parse(fileContents);
      collection.findOneAndDelete(eq("_id", doc.getObjectId("_id")));
      collection.insertOne(doc);
    }

    collection = db.getCollection(workflowCollectionPrefix + "settings");
    Document workers = collection.find(eq("key", "controller")).first();
    List<Document> configs = (List<Document>) workers.get("config");

    for (Document config : configs) {
      if (config.get("key").equals("worker.image")) {
        config.put("value", "boomerangio/worker-flow:2.5.47");
      }
    }

    workers.put("config", configs);
    collection.replaceOne(eq("key", "controller"), workers);
  }

  @ChangeSet(order = "050", id = "050", author = "Adrienne Hudson")
  public void templateUpdates(MongoDatabase db) throws IOException {

    MongoCollection<Document> collection =
        db.getCollection(workflowCollectionPrefix + "task_templates");

    final List<String> files = fileloadingService.loadFiles("flow/050/flow_task_templates/*.json");
    for (final String fileContents : files) {
      final Document doc = Document.parse(fileContents);
      collection.findOneAndDelete(eq("_id", doc.getObjectId("_id")));
      collection.insertOne(doc);
    }
  }

  @ChangeSet(order = "051", id = "051", author = "Adrienne Hudson")
  public void tasktemplateUpdates(MongoDatabase db) throws IOException {

    MongoCollection<Document> collection =
        db.getCollection(workflowCollectionPrefix + "task_templates");

    final List<String> files = fileloadingService.loadFiles("flow/051/flow_task_templates/*.json");
    for (final String fileContents : files) {
      final Document doc = Document.parse(fileContents);
      collection.findOneAndDelete(eq("_id", doc.getObjectId("_id")));
      collection.insertOne(doc);
    }

    final FindIterable<Document> taskTemplates = collection.find();
    for (Document taskTemplate : taskTemplates) {
      if (taskTemplate.get("category").equals("workflow")) {
        taskTemplate.put("category", "Workflow");
        collection.replaceOne(eq("_id", taskTemplate.getObjectId("_id")), taskTemplate);
      }
    }
  }

  @ChangeSet(order = "052", id = "052", author = "Adrienne Hudson")
  public void addTemplates(MongoDatabase db) throws IOException {
    MongoCollection<Document> collection =
        db.getCollection(workflowCollectionPrefix + "task_templates");

    final List<String> files = fileloadingService.loadFiles("flow/052/flow_task_templates/*.json");
    for (final String fileContents : files) {
      final Document doc = Document.parse(fileContents);
      collection.findOneAndDelete(eq("_id", doc.getObjectId("_id")));
      collection.insertOne(doc);
    }
  }

  @ChangeSet(order = "053", id = "053", author = "Adrienne Hudson")
  public void updatingTasktemplates(MongoDatabase db) throws IOException {

    MongoCollection<Document> collection =
        db.getCollection(workflowCollectionPrefix + "task_templates");

    final List<String> files = fileloadingService.loadFiles("flow/053/flow_task_templates/*.json");
    for (final String fileContents : files) {
      final Document doc = Document.parse(fileContents);
      collection.findOneAndDelete(eq("_id", doc.getObjectId("_id")));
      collection.insertOne(doc);
    }
  }

  @ChangeSet(order = "054", id = "054", author = "Adrienne Hudson")
  public void updateTasktemplate(MongoDatabase db) throws IOException {

    MongoCollection<Document> collection =
        db.getCollection(workflowCollectionPrefix + "task_templates");

    final List<String> files = fileloadingService.loadFiles("flow/054/flow_task_templates/*.json");
    for (final String fileContents : files) {
      final Document doc = Document.parse(fileContents);
      collection.findOneAndDelete(eq("_id", doc.getObjectId("_id")));
      collection.insertOne(doc);
    }
  }

  @ChangeSet(order = "055", id = "055", author = "Adrienne Hudson")
  public void updatingTemplate(MongoDatabase db) throws IOException {

    MongoCollection<Document> collection =
        db.getCollection(workflowCollectionPrefix + "task_templates");

    final List<String> files = fileloadingService.loadFiles("flow/055/flow_task_templates/*.json");
    for (final String fileContents : files) {
      final Document doc = Document.parse(fileContents);
      collection.findOneAndDelete(eq("_id", doc.getObjectId("_id")));
      collection.insertOne(doc);
    }
  }

  @ChangeSet(order = "056", id = "056", author = "Adrienne Hudson")
  public void updateTasktemplates(MongoDatabase db) throws IOException {

    MongoCollection<Document> collection =
        db.getCollection(workflowCollectionPrefix + "task_templates");

    final List<String> files = fileloadingService.loadFiles("flow/056/flow_task_templates/*.json");
    for (final String fileContents : files) {
      final Document doc = Document.parse(fileContents);
      collection.findOneAndDelete(eq("_id", doc.getObjectId("_id")));
      collection.insertOne(doc);
    }
  }

  @ChangeSet(order = "057", id = "057", author = "Adrienne Hudson")
  public void updateIndexes(MongoDatabase db) throws IOException {

    MongoCollection<Document> collection =
        db.getCollection(workflowCollectionPrefix + "workflows_activity_task");

    ListIndexesIterable<Document> indexes = collection.listIndexes();
    for (Document index : indexes) {
      if (index.get("name").toString().startsWith("activityId_1")
          || index.get("name").toString().startsWith("activityId_1_taskId_1")) {
        collection.dropIndex(index.get("name").toString());
      }
    }

    collection.createIndex(Indexes.ascending("activityId"));
    collection.createIndex(Indexes.ascending("activityId", "taskId"));

  }

  @ChangeSet(order = "058", id = "058", author = "Adrienne Hudson")
  public void taskTemplatesUpdate(MongoDatabase db) throws IOException {

    MongoCollection<Document> collection =
        db.getCollection(workflowCollectionPrefix + "task_templates");

    final List<String> files = fileloadingService.loadFiles("flow/058/flow_task_templates/*.json");
    for (final String fileContents : files) {
      final Document doc = Document.parse(fileContents);
      collection.findOneAndDelete(eq("_id", doc.getObjectId("_id")));
      collection.insertOne(doc);
    }

    final FindIterable<Document> taskTemplates = collection.find();
    for (Document taskTemplate : taskTemplates) {
      if (taskTemplate.get("name").equals("Read Parameters from File")) {
        taskTemplate.put("status", "inactive");
        collection.replaceOne(eq("_id", taskTemplate.getObjectId("_id")), taskTemplate);
      }
    }
  }

  @ChangeSet(order = "059", id = "059", author = "Adrienne Hudson")
  public void updateWorkerImage(MongoDatabase db) throws IOException {
    MongoCollection<Document> collection = db.getCollection(workflowCollectionPrefix + "settings");
    Document workers = collection.find(eq("name", "Task Configuration")).first();
    List<Document> configs = (List<Document>) workers.get("config");

    for (Document config : configs) {
      if (config.get("key").equals("worker.image")) {
        config.put("value", "boomerangio/worker-flow:2.8.2");
      }
    }

    workers.put("config", configs);
    collection.replaceOne(eq("name", "Task Configuration"), workers);
  }

  @ChangeSet(order = "060", id = "060", author = "Adrienne Hudson")
  public void taskTemplatesUpdates(MongoDatabase db) throws IOException {

    MongoCollection<Document> collection =
        db.getCollection(workflowCollectionPrefix + "task_templates");

    final List<String> files = fileloadingService.loadFiles("flow/060/flow_task_templates/*.json");
    for (final String fileContents : files) {
      final Document doc = Document.parse(fileContents);
      collection.findOneAndDelete(eq("_id", doc.getObjectId("_id")));
      collection.insertOne(doc);
    }
  }

  @ChangeSet(order = "061", id = "061", author = "Adrienne Hudson")
  public void updatingWorkerImage(MongoDatabase db) throws IOException {
    MongoCollection<Document> collection = db.getCollection(workflowCollectionPrefix + "settings");
    Document workers = collection.find(eq("name", "Task Configuration")).first();
    List<Document> configs = (List<Document>) workers.get("config");

    for (Document config : configs) {
      if (config.get("key").equals("worker.image")) {
        config.put("value", "boomerangio/worker-flow:2.8.3");
      }
    }

    workers.put("config", configs);
    collection.replaceOne(eq("name", "Task Configuration"), workers);
  }

  @ChangeSet(order = "062", id = "062", author = "Adrienne Hudson")
  public void updateTaskConfigurationSettings(MongoDatabase db) throws IOException {
    MongoCollection<Document> collection = db.getCollection(workflowCollectionPrefix + "settings");
    Document setting = collection.find(eq("name", "Task Configuration")).first();
    List<Document> config = (List<Document>) setting.get("config");

    Document newConfig = new Document();
    newConfig.put("description", "Task Timeout Configuration specified in minutes");
    newConfig.put("key", "task.timeout.configuration");
    newConfig.put("label", "Task Timeout Configuration");
    newConfig.put("type", "number");
    newConfig.put("value", "90");
    newConfig.put("readOnly", false);

    config.add(newConfig);

    setting.put("config", config);
    collection.replaceOne(eq("name", "Task Configuration"), setting);
  }

  @ChangeSet(order = "063", id = "063", author = "Adrienne Hudson")
  public void tasktempateUpdates(MongoDatabase db) throws IOException {

    MongoCollection<Document> collection =
        db.getCollection(workflowCollectionPrefix + "task_templates");

    final List<String> files = fileloadingService.loadFiles("flow/063/flow_task_templates/*.json");
    for (final String fileContents : files) {
      final Document doc = Document.parse(fileContents);
      collection.findOneAndDelete(eq("_id", doc.getObjectId("_id")));
      collection.insertOne(doc);
    }
  }

  @ChangeSet(order = "064", id = "064", author = "Adrienne Hudson")
  public void updateWorker(MongoDatabase db) throws IOException {
    MongoCollection<Document> collection = db.getCollection(workflowCollectionPrefix + "settings");
    Document workers = collection.find(eq("name", "Task Configuration")).first();
    List<Document> configs = (List<Document>) workers.get("config");

    for (Document config : configs) {
      if (config.get("key").equals("worker.image")) {
        config.put("value", "boomerangio/worker-flow:2.8.11");
      }
    }

    workers.put("config", configs);
    collection.replaceOne(eq("name", "Task Configuration"), workers);
  }

  @ChangeSet(order = "065", id = "065", author = "Adrienne Hudson")
  public void updateTaskTemplateRevisionCommand(MongoDatabase db) throws IOException {
    MongoCollection<Document> collection =
        db.getCollection(workflowCollectionPrefix + "task_templates");

    final FindIterable<Document> taskTemplates = collection.find();
    for (Document taskTemplate : taskTemplates) {

      List<Document> revisions = (List<Document>) taskTemplate.get("revisions");
      for (Document revision : revisions) {
        try {
          String command = revision.getString("command");
          List<String> updatedCommand = new ArrayList<>();
          if (command != null && !command.isBlank()) {
            updatedCommand.add(command);
          }
          revision.put("command", updatedCommand);
          collection.replaceOne(eq("_id", taskTemplate.getObjectId("_id")), taskTemplate);
        } catch (ClassCastException e) {

        }
      }
    }

  }

  @ChangeSet(order = "066", id = "066", author = "Adrienne Hudson")
  public void tasktemplateUpdate(MongoDatabase db) throws IOException {

    MongoCollection<Document> collection =
        db.getCollection(workflowCollectionPrefix + "task_templates");

    final List<String> files = fileloadingService.loadFiles("flow/066/flow_task_templates/*.json");
    for (final String fileContents : files) {
      final Document doc = Document.parse(fileContents);
      collection.findOneAndDelete(eq("_id", doc.getObjectId("_id")));
      collection.insertOne(doc);
    }
  }

  @ChangeSet(order = "067", id = "067", author = "Adrienne Hudson")
  public void updatingtaskTemplate(MongoDatabase db) throws IOException {

    MongoCollection<Document> collection =
        db.getCollection(workflowCollectionPrefix + "task_templates");

    final List<String> files = fileloadingService.loadFiles("flow/067/flow_task_templates/*.json");
    for (final String fileContents : files) {
      final Document doc = Document.parse(fileContents);
      collection.findOneAndDelete(eq("_id", doc.getObjectId("_id")));
      collection.insertOne(doc);
    }
  }

  @ChangeSet(order = "068", id = "068", author = "Marcus Roy")
  public void updateSettings(MongoDatabase db) throws IOException {

    final MongoCollection<Document> collection =
        db.getCollection(workflowCollectionPrefix + "settings");

    final List<String> files = fileloadingService.loadFiles("flow/068/flow_settings/*.json");

    for (final String fileContents : files) {
      final Document doc = Document.parse(fileContents);
      collection.insertOne(doc);
    }
  }

  @ChangeSet(order = "069", id = "069", author = "Adrienne Hudson")
  public void addingFlowSetting(MongoDatabase db) throws IOException {

    final MongoCollection<Document> collection =
        db.getCollection(workflowCollectionPrefix + "settings");
    final List<String> files = fileloadingService.loadFiles("flow/069/flow_settings/*.json");
    for (final String fileContents : files) {
      final Document doc = Document.parse(fileContents);
      collection.insertOne(doc);
    }
  }

  @ChangeSet(order = "070", id = "070", author = "Adrienne Hudson")
  public void addTasktTemplateAndUpdateWorkerImage(MongoDatabase db) throws IOException {

    MongoCollection<Document> collection =
        db.getCollection(workflowCollectionPrefix + "task_templates");

    final List<String> files = fileloadingService.loadFiles("flow/070/flow_task_templates/*.json");
    for (final String fileContents : files) {
      final Document doc = Document.parse(fileContents);
      collection.findOneAndDelete(eq("_id", doc.getObjectId("_id")));
      collection.insertOne(doc);
    }

    collection = db.getCollection(workflowCollectionPrefix + "settings");
    Document workers = collection.find(eq("name", "Task Configuration")).first();
    List<Document> configs = (List<Document>) workers.get("config");

    for (Document config : configs) {
      if (config.get("key").equals("worker.image")) {
        config.put("value", "boomerangio/worker-flow:2.9.1");
      }
    }

    workers.put("config", configs);
    collection.replaceOne(eq("name", "Task Configuration"), workers);
  }

  @ChangeSet(order = "071", id = "071", author = "Adrienne Hudson")
  public void verifyTaskTemplate(MongoDatabase db) throws IOException {
    final MongoCollection<Document> flowTaskTemplateCollection =
        db.getCollection(workflowCollectionPrefix + "task_templates");

    final FindIterable<Document> flowTemplates = flowTaskTemplateCollection.find();
    for (final Document flowTemplate : flowTemplates) {
      if (flowTemplate.get("name").equals("Send Email with Postmark Template")) {
        flowTemplate.put("verified", true);
        flowTaskTemplateCollection.replaceOne(eq("_id", flowTemplate.getObjectId("_id")),
            flowTemplate);
      }
    }
  }

  @ChangeSet(order = "072", id = "072", author = "Adrienne Hudson")
  public void addFlowSetting(MongoDatabase db) throws IOException {

    final MongoCollection<Document> collection =
        db.getCollection(workflowCollectionPrefix + "settings");
    final List<String> files = fileloadingService.loadFiles("flow/072/flow_settings/*.json");
    for (final String fileContents : files) {
      final Document doc = Document.parse(fileContents);
      collection.insertOne(doc);
    }
  }

  @ChangeSet(order = "073", id = "073", author = "Adrienne Hudson")
  public void migrateEnablePersistentStorage(MongoDatabase db) throws IOException {

    final MongoCollection<Document> flowWorkflowsCollection =
        db.getCollection(workflowCollectionPrefix + "workflows");
    final FindIterable<Document> flowWorkflows = flowWorkflowsCollection.find();
    for (final Document flowWorkflow : flowWorkflows) {

      boolean enablePersistentStorage = (boolean) flowWorkflow.get("enablePersistentStorage");

      Document storage =
          (Document) flowWorkflow.get("storage") != null ? (Document) flowWorkflow.get("storage")
              : new Document();

      Document workflowStorage =
          (Document) storage.get("workflow") != null ? (Document) storage.get("workflow")
              : new Document();

      workflowStorage.put("enabled", enablePersistentStorage);
      storage.put("workflow", workflowStorage);

      flowWorkflow.put("storage", storage);
      flowWorkflow.remove("enablePersistentStorage");

      flowWorkflowsCollection.replaceOne(eq("_id", flowWorkflow.getObjectId("_id")), flowWorkflow);

    }
  }

  @ChangeSet(order = "074", id = "074", author = "Adrienne Hudson")
  public void updateFlowSettings(MongoDatabase db) throws IOException {

    Document newConfig = new Document();
    newConfig.put("description", "Maximum storage size");
    newConfig.put("key", "max.storage.size");
    newConfig.put("label", "Maximum storage size");
    newConfig.put("type", "text");
    newConfig.put("value", "5Gi");
    newConfig.put("readOnly", false);

    final MongoCollection<Document> collection =
        db.getCollection(workflowCollectionPrefix + "settings");
    Document workspaceSetting = collection.find(eq("key", "workspace")).first();
    workspaceSetting.put("name", "Workspace Configuration - Workflow Storage");
    List<Document> configs = (List<Document>) workspaceSetting.get("config");
    configs.add(newConfig);
    collection.replaceOne(eq("_id", workspaceSetting.getObjectId("_id")), workspaceSetting);


    Document workflowSetting = collection.find(eq("key", "workflow")).first();
    workflowSetting.put("name", "Workspace Configuration - Activity Storage");
    configs = (List<Document>) workflowSetting.get("config");
    configs.add(newConfig);
    collection.replaceOne(eq("_id", workflowSetting.getObjectId("_id")), workflowSetting);


    Document teamDefaults = collection.find(eq("key", "teams")).first();
    configs = (List<Document>) teamDefaults.get("config");
    for (Document config : configs) {
      if (config.get("key").equals("max.team.workflow.storage")) {
        config.put("description", "Maximum Storage allowed Per Workflow across executions");
        config.put("type", "text");
        config.put("value", "25Gi");
      }
    }
    teamDefaults.put("config", configs);
    collection.replaceOne(eq("_id", teamDefaults.getObjectId("_id")), teamDefaults);


    newConfig.put("key", "max.user.workflow.storage");
    newConfig.put("description", "Maximum Storage allowed Per Workflow across executions");
    newConfig.put("type", "text");
    newConfig.put("value", "25Gi");
    newConfig.put("label", "Total Storage");
    newConfig.put("readOnly", false);

    Document userDefaults = collection.find(eq("key", "users")).first();
    configs = (List<Document>) userDefaults.get("config");
    configs.add(newConfig);
    collection.replaceOne(eq("_id", userDefaults.getObjectId("_id")), userDefaults);

  }


  @ChangeSet(order = "075", id = "075", author = "Adrienne Hudson")
  public void workerImageUpdate(MongoDatabase db) throws IOException {
    MongoCollection<Document> collection = db.getCollection(workflowCollectionPrefix + "settings");
    Document workers = collection.find(eq("name", "Task Configuration")).first();
    List<Document> configs = (List<Document>) workers.get("config");

    for (Document config : configs) {
      if (config.get("key").equals("worker.image")) {
        config.put("value", "boomerangio/worker-flow:2.9.4");
      }
    }

    workers.put("config", configs);
    collection.replaceOne(eq("name", "Task Configuration"), workers);
  }

  @ChangeSet(order = "076", id = "076", author = "Adrienne Hudson")
  public void workflowStorageMigration(MongoDatabase db) throws IOException {

    final MongoCollection<Document> flowWorkflowsCollection =
        db.getCollection(workflowCollectionPrefix + "workflows");
    final FindIterable<Document> flowWorkflows = flowWorkflowsCollection.find();
    for (final Document flowWorkflow : flowWorkflows) {

      Document storage = (Document) flowWorkflow.get("storage");

      if (storage != null) {
        if (storage.get("workflow") != null) {
          Document activity = (Document) storage.get("workflow");
          storage.put("activity", activity);
          storage.remove("workflow");
        }
        if (storage.get("workspace") != null) {
          Document workflow = (Document) storage.get("workspace");
          storage.put("workflow", workflow);
          storage.remove("workspace");
        }
      }

      flowWorkflowsCollection.replaceOne(eq("_id", flowWorkflow.getObjectId("_id")), flowWorkflow);

    }
  }

  @ChangeSet(order = "077", id = "077", author = "Adrienne Hudson")
  public void updatingTaskTemplate(MongoDatabase db) throws IOException {

    MongoCollection<Document> collection =
        db.getCollection(workflowCollectionPrefix + "task_templates");

    final List<String> files = fileloadingService.loadFiles("flow/077/flow_task_templates/*.json");
    for (final String fileContents : files) {
      final Document doc = Document.parse(fileContents);
      collection.findOneAndDelete(eq("_id", doc.getObjectId("_id")));
      collection.insertOne(doc);
    }
  }

  @ChangeSet(order = "078", id = "078", author = "Adrienne Hudson")
  public void addingAddIssueTaskTemplate(MongoDatabase db) throws IOException {

    MongoCollection<Document> collection =
        db.getCollection(workflowCollectionPrefix + "task_templates");

    final List<String> files = fileloadingService.loadFiles("flow/078/flow_task_templates/*.json");
    for (final String fileContents : files) {
      final Document doc = Document.parse(fileContents);
      collection.findOneAndDelete(eq("_id", doc.getObjectId("_id")));
      collection.insertOne(doc);
    }
  }

  @ChangeSet(order = "079", id = "079", author = "Adrienne Hudson")
  public void updateworkerImage(MongoDatabase db) throws IOException {
    MongoCollection<Document> collection = db.getCollection(workflowCollectionPrefix + "settings");
    Document workers = collection.find(eq("name", "Task Configuration")).first();
    List<Document> configs = (List<Document>) workers.get("config");

    for (Document config : configs) {
      if (config.get("key").equals("worker.image")) {
        config.put("value", "boomerangio/worker-flow:2.9.18");
      }
    }

    workers.put("config", configs);
    collection.replaceOne(eq("name", "Task Configuration"), workers);
  }


  @ChangeSet(order = "080", id = "080", author = "Adrienne Hudson")
  public void updateworkerimage(MongoDatabase db) throws IOException {
    MongoCollection<Document> collection = db.getCollection(workflowCollectionPrefix + "settings");
    Document workers = collection.find(eq("name", "Task Configuration")).first();
    List<Document> configs = (List<Document>) workers.get("config");

    for (Document config : configs) {
      if (config.get("key").equals("worker.image")) {
        config.put("value", "boomerangio/worker-flow:2.9.20");
      }
    }

    workers.put("config", configs);
    collection.replaceOne(eq("name", "Task Configuration"), workers);
  }

  @ChangeSet(order = "081", id = "081", author = "George Safta")
  public void addGoogleSheetsTaskTemplates(MongoDatabase db) throws IOException {
    MongoCollection<Document> collection =
        db.getCollection(workflowCollectionPrefix + "task_templates");

    final List<String> files = fileloadingService.loadFiles("flow/081/flow_task_templates/*.json");
    for (final String fileContents : files) {
      final Document doc = Document.parse(fileContents);
      collection.findOneAndDelete(eq("_id", doc.getObjectId("_id")));
      collection.insertOne(doc);
    }
  }

  @ChangeSet(order = "082", id = "082", author = "Adrienne Hudson")
  public void updateSendEmailWithPostmarkTemplate(MongoDatabase db) throws IOException {
    MongoCollection<Document> collection =
        db.getCollection(workflowCollectionPrefix + "task_templates");

    final List<String> files = fileloadingService.loadFiles("flow/082/flow_task_templates/*.json");
    for (final String fileContents : files) {
      final Document doc = Document.parse(fileContents);
      collection.findOneAndDelete(eq("_id", doc.getObjectId("_id")));
      collection.insertOne(doc);
    }
  }

  @ChangeSet(order = "083", id = "083", author = "Adrienne Hudson")
  public void updateQuartzJobClassName(MongoDatabase db) throws IOException {

    MongoCollection<Document> collection = db.getCollection(workflowCollectionPrefix + "jobs");

    final FindIterable<Document> taskTemplates = collection.find();
    for (Document job : taskTemplates) {
      job.put("jobClass", "io.boomerang.quartz.WorkflowExecuteJob");
      collection.replaceOne(eq("_id", job.getObjectId("_id")), job);
    }
  }

  @ChangeSet(order = "084", id = "084", author = "Adrienne Hudson")
  public void migrationWorkflowScheduleTriggers(MongoDatabase db) throws IOException {

    MongoCollection<Document> workflowScheduleCollection =
        db.getCollection(workflowCollectionPrefix + "workflows_schedules");
    if (workflowScheduleCollection == null) {
      db.createCollection(workflowCollectionPrefix + "workflows_schedules");
    }

    final MongoCollection<Document> flowWorkflowsCollection =
        db.getCollection(workflowCollectionPrefix + "workflows");

    final FindIterable<Document> workflowEntities = flowWorkflowsCollection.find();
    for (final Document workflowEntity : workflowEntities) {
      Document triggers = (Document) workflowEntity.get("triggers");
      if (triggers != null) {
        Document scheduler = (Document) triggers.get("scheduler");

        if (workflowScheduleCollection.find(eq("workflowId", workflowEntity.get("_id").toString()))
            .first() == null && workflowEntity.get("status").equals("active")) {

          Document schedule = new Document();

          if (scheduler.get("advancedCron") != null && scheduler.get("advancedCron").equals(true)) {
            schedule.put("type", "advancedCron");
          } else {
            schedule.put("type", "cron");
          }

          scheduler.remove("advancedCron");

          if (scheduler.get("enable") != null && scheduler.get("enable").equals(true)) {
            schedule.put("status", "active");
          } else {
            schedule.put("status", "inactive");
          }

          schedule.put("timezone", scheduler.get("timezone"));
          scheduler.remove("timezone");

          schedule.put("cronSchedule", scheduler.get("schedule"));
          scheduler.remove("cronSchedule");

          schedule.put("workflowId", workflowEntity.get("_id").toString());
          schedule.put("name", "Migrated Schedule");
          schedule.put("description", "");
          schedule.put("creationDate", new Date());
          schedule.put("labels", new ArrayList<>());

          List<Document> parameters = new ArrayList<>();
          for (Document property : (List<Document>) workflowEntity.get("properties")) {
            Document parameter = new Document();
            parameter.put("key", property.get("key"));
            parameter.put("value", property.get("defaultValue"));
            parameters.add(parameter);
          }

          schedule.put("parameters", parameters);

          workflowScheduleCollection.insertOne(schedule);
        }
      }

      flowWorkflowsCollection.replaceOne(eq("_id", workflowEntity.getObjectId("_id")),
          workflowEntity);
    }
  }


  @ChangeSet(order = "085", id = "085", author = "Adrienne Hudson")
  public void addingRunScheduledWorkflowTask(MongoDatabase db) throws IOException {

    MongoCollection<Document> collection =
        db.getCollection(workflowCollectionPrefix + "task_templates");

    final List<String> files = fileloadingService.loadFiles("flow/085/flow_task_templates/*.json");
    for (final String fileContents : files) {
      final Document doc = Document.parse(fileContents);
      collection.findOneAndDelete(eq("_id", doc.getObjectId("_id")));
      collection.insertOne(doc);
    }
  }

  @ChangeSet(order = "086", id = "086", author = "Adrienne Hudson")
  public void updateTeamAndUserSettings(MongoDatabase db) throws IOException {
    MongoCollection<Document> collection = db.getCollection(workflowCollectionPrefix + "settings");
    Document teamDefaults = collection.find(eq("name", "Team Defaults")).first();
    List<Document> configs = (List<Document>) teamDefaults.get("config");

    for (Document config : configs) {
      if (config.get("key").equals("max.team.workflow.duration")) {
        config.put("description",
            "The maximum time that a workflow can be executing for in minutes");
        config.put("label", "Maximum Workflow Execution Duration");
      }
    }

    teamDefaults.put("config", configs);
    collection.replaceOne(eq("name", "Team Defaults"), teamDefaults);

    Document userDefaults = collection.find(eq("name", "User Defaults")).first();
    configs = (List<Document>) userDefaults.get("config");

    for (Document config : configs) {
      if (config.get("key").equals("max.user.workflow.duration")) {
        config.put("description",
            "The maximum time that a workflow can be executing for in minutes");
        config.put("label", "Maximum Workflow Execution Duration");
      }
    }

    userDefaults.put("config", configs);
    collection.replaceOne(eq("name", "User Defaults"), userDefaults);
  }


  @ChangeSet(order = "087", id = "087", author = "Adrienne Hudson")
  public void updateTaskConfigurationSetting(MongoDatabase db) throws IOException {

    MongoCollection<Document> collection = db.getCollection(workflowCollectionPrefix + "settings");
    Document controller = collection.find(eq("key", "controller")).first();

    List<Document> configs = (List<Document>) controller.get("config");
    for (Document config : configs) {
      if (config.get("key").equals("job.deletion.policy")) {
        config.put("label", "Deletion Policy");
        config.put("description", "Defines the completion state that will lead to worker removal");
      }
    }
    controller.put("config", configs);
    collection.replaceOne(eq("key", "controller"), controller);
  }

  @ChangeSet(order = "088", id = "088", author = "Adrienne Hudson")
  public void updatedefaultworkerimage(MongoDatabase db) throws IOException {
    MongoCollection<Document> collection = db.getCollection(workflowCollectionPrefix + "settings");
    Document workers = collection.find(eq("name", "Task Configuration")).first();
    List<Document> configs = (List<Document>) workers.get("config");

    for (Document config : configs) {
      if (config.get("key").equals("worker.image")) {
        config.put("value", "boomerangio/worker-flow:2.9.22");
      }
    }

    workers.put("config", configs);
    collection.replaceOne(eq("name", "Task Configuration"), workers);
  }

  @ChangeSet(order = "089", id = "089", author = "Adrienne Hudson")
  public void updatingHTTPTaskTemplates(MongoDatabase db) throws IOException {

    MongoCollection<Document> collection =
        db.getCollection(workflowCollectionPrefix + "task_templates");

    final List<String> files = fileloadingService.loadFiles("flow/089/flow_task_templates/*.json");
    for (final String fileContents : files) {
      final Document doc = Document.parse(fileContents);
      collection.findOneAndDelete(eq("_id", doc.getObjectId("_id")));
      collection.insertOne(doc);
    }
  }

  @ChangeSet(order = "090", id = "090", author = "Adrienne Hudson")
  public void updatedefaultworker(MongoDatabase db) throws IOException {
    MongoCollection<Document> collection = db.getCollection(workflowCollectionPrefix + "settings");
    Document workers = collection.find(eq("name", "Task Configuration")).first();
    List<Document> configs = (List<Document>) workers.get("config");

    for (Document config : configs) {
      if (config.get("key").equals("worker.image")) {
        config.put("value", "boomerangio/worker-flow:2.10.16");
      }
    }

    workers.put("config", configs);
    collection.replaceOne(eq("name", "Task Configuration"), workers);
  }

  @ChangeSet(order = "091", id = "091", author = "Adrienne Hudson")
  public void updateWorkspaceConfigurationsKey(MongoDatabase db) throws IOException {
    MongoCollection<Document> collection = db.getCollection(workflowCollectionPrefix + "settings");

    Document workflowStorage =
        collection.find(eq("name", "Workspace Configuration - Workflow Storage")).first();
    workflowStorage.put("key", "workflow");

    collection.replaceOne(eq("name", "Workspace Configuration - Workflow Storage"),
        workflowStorage);

    Document activityStorage =
        collection.find(eq("name", "Workspace Configuration - Activity Storage")).first();
    activityStorage.put("key", "activity");

    collection.replaceOne(eq("name", "Workspace Configuration - Activity Storage"),
        activityStorage);
  }

  @ChangeSet(order = "092", id = "092", author = "Adrienne Hudson")
  public void updatingtasktemplate(MongoDatabase db) throws IOException {

    MongoCollection<Document> collection =
        db.getCollection(workflowCollectionPrefix + "task_templates");

    final List<String> files = fileloadingService.loadFiles("flow/092/flow_task_templates/*.json");
    for (final String fileContents : files) {
      final Document doc = Document.parse(fileContents);
      collection.findOneAndDelete(eq("_id", doc.getObjectId("_id")));
      collection.insertOne(doc);
    }
  }

  @ChangeSet(order = "093", id = "093", author = "Adrienne Hudson")
  public void updatingtemplate(MongoDatabase db) throws IOException {

    MongoCollection<Document> collection =
        db.getCollection(workflowCollectionPrefix + "task_templates");

    final List<String> files = fileloadingService.loadFiles("flow/093/flow_task_templates/*.json");
    for (final String fileContents : files) {
      final Document doc = Document.parse(fileContents);
      collection.findOneAndDelete(eq("_id", doc.getObjectId("_id")));
      collection.insertOne(doc);
    }
  }

  @ChangeSet(order = "094", id = "094", author = "Adrienne Hudson")
  public void updateworker(MongoDatabase db) throws IOException {
    MongoCollection<Document> collection = db.getCollection(workflowCollectionPrefix + "settings");
    Document workers = collection.find(eq("name", "Task Configuration")).first();
    List<Document> configs = (List<Document>) workers.get("config");

    for (Document config : configs) {
      if (config.get("key").equals("worker.image")) {
        config.put("value", "boomerangio/worker-flow:2.10.19");
      }
    }

    workers.put("config", configs);
    collection.replaceOne(eq("name", "Task Configuration"), workers);
  }

  @ChangeSet(order = "095", id = "095", author = "Adrienne Hudson")
  public void updatetemplate(MongoDatabase db) throws IOException {

    MongoCollection<Document> collection =
        db.getCollection(workflowCollectionPrefix + "task_templates");

    final List<String> files = fileloadingService.loadFiles("flow/095/flow_task_templates/*.json");
    for (final String fileContents : files) {
      final Document doc = Document.parse(fileContents);
      collection.findOneAndDelete(eq("_id", doc.getObjectId("_id")));
      collection.insertOne(doc);
    }
  }

  @ChangeSet(order = "096", id = "096", author = "Adrienne Hudson")
  public void updatetasktemplate(MongoDatabase db) throws IOException {

    MongoCollection<Document> collection =
        db.getCollection(workflowCollectionPrefix + "task_templates");

    final List<String> files = fileloadingService.loadFiles("flow/096/flow_task_templates/*.json");
    for (final String fileContents : files) {
      final Document doc = Document.parse(fileContents);
      collection.findOneAndDelete(eq("_id", doc.getObjectId("_id")));
      collection.insertOne(doc);
    }
  }

  @ChangeSet(order = "097", id = "097", author = "Adrienne Hudson")
  public void removeDuplicateTemplate(MongoDatabase db) throws IOException {
    MongoCollection<Document> collection =
        db.getCollection(workflowCollectionPrefix + "task_templates");
    collection.findOneAndDelete(eq("_id", new ObjectId("61f2a522aff34c34ea43198c")));
  }

  @ChangeSet(order = "098", id = "098", author = "Adrienne Hudson")
  public void updatetaskTemplates(MongoDatabase db) throws IOException {
    MongoCollection<Document> collection =
        db.getCollection(workflowCollectionPrefix + "task_templates");
    final List<String> files = fileloadingService.loadFiles("flow/098/flow_task_templates/*.json");
    for (final String fileContents : files) {
      final Document doc = Document.parse(fileContents);
      collection.findOneAndDelete(eq("_id", doc.getObjectId("_id")));
      collection.insertOne(doc);
    }
  }

  @ChangeSet(order = "099", id = "099", author = "Adrienne Hudson")
  public void updatetasktemplates(MongoDatabase db) throws IOException {
    MongoCollection<Document> collection =
        db.getCollection(workflowCollectionPrefix + "task_templates");
    final List<String> files = fileloadingService.loadFiles("flow/099/flow_task_templates/*.json");
    for (final String fileContents : files) {
      final Document doc = Document.parse(fileContents);
      collection.findOneAndDelete(eq("_id", doc.getObjectId("_id")));
      collection.insertOne(doc);
    }
  }

  @ChangeSet(order = "100", id = "100", author = "Adrienne Hudson")
  public void updateimage(MongoDatabase db) throws IOException {
    MongoCollection<Document> collection = db.getCollection(workflowCollectionPrefix + "settings");
    Document workers = collection.find(eq("name", "Task Configuration")).first();
    List<Document> configs = (List<Document>) workers.get("config");

    for (Document config : configs) {
      if (config.get("key").equals("worker.image")) {
        config.put("value", "boomerangio/worker-flow:2.11.6");
      }
    }
    workers.put("config", configs);
    collection.replaceOne(eq("name", "Task Configuration"), workers);
  }

  @ChangeSet(order = "101", id = "101", author = "Adrienne Hudson")
  public void updatedefaultimage(MongoDatabase db) throws IOException {
    MongoCollection<Document> collection = db.getCollection(workflowCollectionPrefix + "settings");
    Document workers = collection.find(eq("name", "Task Configuration")).first();
    List<Document> configs = (List<Document>) workers.get("config");

    for (Document config : configs) {
      if (config.get("key").equals("worker.image")) {
        config.put("value", "boomerangio/worker-flow:2.11.7");
      }
    }
    workers.put("config", configs);
    collection.replaceOne(eq("name", "Task Configuration"), workers);
  }

  @ChangeSet(order = "102", id = "102", author = "Adrienne Hudson")
  public void updateExecuteAdvancedHTTPCallTaskTemplate(MongoDatabase db) throws IOException {
    MongoCollection<Document> collection =
        db.getCollection(workflowCollectionPrefix + "task_templates");
    final List<String> files = fileloadingService.loadFiles("flow/102/flow_task_templates/*.json");
    for (final String fileContents : files) {
      final Document doc = Document.parse(fileContents);
      collection.findOneAndDelete(eq("_id", doc.getObjectId("_id")));
      collection.insertOne(doc);
    }
  }

  @ChangeSet(order = "103", id = "103", author = "Adrienne Hudson")
  public void updateBoxTaskTemplates(MongoDatabase db) throws IOException {
    MongoCollection<Document> collection =
        db.getCollection(workflowCollectionPrefix + "task_templates");
    final List<String> files = fileloadingService.loadFiles("flow/103/flow_task_templates/*.json");
    for (final String fileContents : files) {
      final Document doc = Document.parse(fileContents);
      collection.findOneAndDelete(eq("_id", doc.getObjectId("_id")));
      collection.insertOne(doc);
    }
  }

  @ChangeSet(order = "104", id = "104", author = "Adrienne Hudson")
  public void addSetting(MongoDatabase db) throws IOException {
    final List<String> files = fileloadingService.loadFiles("flow/104/flow_settings/*.json");
    for (final String fileContents : files) {
      final Document doc = Document.parse(fileContents);
      final MongoCollection<Document> collection =
          db.getCollection(workflowCollectionPrefix + "settings");
      collection.insertOne(doc);
    }
  }

  @ChangeSet(order = "105", id = "105", author = "Adrienne Hudson")
  public void addingSetting(MongoDatabase db) throws IOException {
    final List<String> files = fileloadingService.loadFiles("flow/105/flow_settings/*.json");
    for (final String fileContents : files) {
      final Document doc = Document.parse(fileContents);
      final MongoCollection<Document> collection =
          db.getCollection(workflowCollectionPrefix + "settings");
      collection.insertOne(doc);
    }
  }

  @ChangeSet(order = "106", id = "106", author = "Adrienne Hudson")
  public void updatedefaultOmage(MongoDatabase db) throws IOException {
    MongoCollection<Document> collection = db.getCollection(workflowCollectionPrefix + "settings");
    Document workers = collection.find(eq("name", "Task Configuration")).first();
    List<Document> configs = (List<Document>) workers.get("config");

    for (Document config : configs) {
      if (config.get("key").equals("worker.image")) {
        config.put("value", "boomerangio/worker-flow:2.11.12");
      }
    }
    workers.put("config", configs);
    collection.replaceOne(eq("name", "Task Configuration"), workers);
  }

  @ChangeSet(order = "107", id = "107", author = "Adrienne Hudson")
  public void addWorkflowTemplates(MongoDatabase db) throws IOException {
    MongoCollection<Document> collection = db.getCollection(workflowCollectionPrefix + "workflows");
    final List<String> files = fileloadingService.loadFiles("flow/107/flow_workflows/*.json");
    for (final String fileContents : files) {
      final Document doc = Document.parse(fileContents);
      collection.findOneAndDelete(eq("_id", doc.getObjectId("_id")));
      collection.insertOne(doc);
    }
  }

  @ChangeSet(order = "108", id = "108", author = "Adrienne Hudson")
  public void removeTemplates(MongoDatabase db) throws IOException {
    MongoCollection<Document> collection = db.getCollection(workflowCollectionPrefix + "workflows");
    collection.findOneAndDelete(eq("name", "Looking through planets with HTTP Call "));
    collection.findOneAndDelete(eq("name", "MongoDB email query results"));
  }

  @ChangeSet(order = "109", id = "109", author = "Adrienne Hudson")
  public void addingWorkflowTemplates(MongoDatabase db) throws IOException {
    MongoCollection<Document> collection = db.getCollection(workflowCollectionPrefix + "workflows");
    final List<String> files = fileloadingService.loadFiles("flow/109/flow_workflows/*.json");
    for (final String fileContents : files) {
      final Document doc = Document.parse(fileContents);
      collection.findOneAndDelete(eq("_id", doc.getObjectId("_id")));
      collection.insertOne(doc);
    }
  }

  @ChangeSet(order = "110", id = "110", author = "Adrienne Hudson")
  public void addingWorkflowRevisions(MongoDatabase db) throws IOException {
    MongoCollection<Document> collection =
        db.getCollection(workflowCollectionPrefix + "workflows_revisions");
    final List<String> files =
        fileloadingService.loadFiles("flow/110/flow_workflows_revisions/*.json");
    for (final String fileContents : files) {
      final Document doc = Document.parse(fileContents);
      collection.findOneAndDelete(eq("_id", doc.getObjectId("_id")));
      collection.insertOne(doc);
    }
  }

  @ChangeSet(order = "111", id = "111", author = "Dylan Landry")
  public void addSlackSigningSetting(MongoDatabase db) throws IOException {
    final MongoCollection<Document> collection =
        db.getCollection(workflowCollectionPrefix + "settings");
    Document extensions = collection.find(eq("name", "Extensions Configuration")).first();
    List<Document> configs = (List<Document>) extensions.get("config");

    Document newConfig = new Document();
    newConfig.put("key", "slack.signingSecret");
    newConfig.put("label", "Slack Signing Secret");
    newConfig.put("value", "");
    newConfig.put("description", "The Signing Secret from your Slack apps credentials.");
    newConfig.put("type", "secured");
    newConfig.put("readOnly", false);

    configs.add(newConfig);
    extensions.put("config", configs);
    collection.replaceOne(eq("name", "Extensions Configuration"), extensions);
  }


  @ChangeSet(order = "112", id = "112", author = "Adrienne Hudson")
  public void updatingdefaultimage(MongoDatabase db) throws IOException {
    MongoCollection<Document> collection = db.getCollection(workflowCollectionPrefix + "settings");
    Document workers = collection.find(eq("name", "Task Configuration")).first();
    List<Document> configs = (List<Document>) workers.get("config");

    for (Document config : configs) {
      if (config.get("key").equals("worker.image")) {
        config.put("value", "boomerangio/worker-flow:2.11.15");
      }
    }
    workers.put("config", configs);
    collection.replaceOne(eq("name", "Task Configuration"), workers);
  }
}
