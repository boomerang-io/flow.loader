package net.boomerangplatform.migration.changesets.flow;

import static com.mongodb.client.model.Filters.eq;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.bson.Document;
import org.bson.conversions.Bson;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.github.cloudyrock.mongock.ChangeLog;
import com.github.cloudyrock.mongock.ChangeSet;
import com.mongodb.BasicDBObject;
import com.mongodb.client.FindIterable;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Updates;
import net.boomerangplatform.migration.FileLoadingService;
import net.boomerangplatform.migration.SpringContextBridge;

@ChangeLog
public class FlowDatabaseChangeLog {

  private static FileLoadingService fileloadingService;

  private final Logger logger = LoggerFactory.getLogger(FlowDatabaseChangeLog.class);

  public FlowDatabaseChangeLog() {
    fileloadingService = SpringContextBridge.services().getFileLoadingService();
  }

  @ChangeSet(order = "001", id = "001", author = "Marcus Roy")
  public void initialSetup(MongoDatabase db) throws IOException {

    logger.info("Running change log: #1 - Creating Collections for Boomerang Flow");

    db.createCollection("flow_task_templates");
    db.createCollection("flow_teams");
    db.createCollection("flow_workflows");
    db.createCollection("flow_workflows_activity");
    db.createCollection("flow_workflows_activity_task");
    db.createCollection("flow_workflows_revisions");
  }

  @ChangeSet(order = "002", id = "002", author = "Marcus Roy")
  public void loadInTemplates(MongoDatabase db) throws IOException {

    logger.info("Running change log: #2 - Loading in flow templates");

    final List<String> files = fileloadingService.loadFiles("flow/001/flow_task_templates/*.json");
    for (final String fileContents : files) {
      final Document doc = Document.parse(fileContents);
      final MongoCollection<Document> collection = db.getCollection("flow_task_templates");
      collection.insertOne(doc);
    }
  }

  @ChangeSet(order = "003", id = "003", author = "Adrienne Hudson")
  public void updateTaskTemplates(MongoDatabase db) throws IOException {

    db.getCollection("flow_task_templates").deleteOne(eq("name", "Execute HTTP Call"));

    final List<String> files = fileloadingService.loadFiles("flow/002/flow_task_templates/*.json");
    for (final String fileContents : files) {
      final Document doc = Document.parse(fileContents);
      final MongoCollection<Document> collection = db.getCollection("flow_task_templates");
      collection.insertOne(doc);

    }
  }

  @ChangeSet(order = "004", id = "004", author = "Adrienne Hudson")
  public void updateTaskTemplate(MongoDatabase db) throws IOException {

    db.getCollection("flow_task_templates").deleteOne(eq("name", "Send Slack Message"));

    final List<String> files = fileloadingService.loadFiles("flow/003/flow_task_templates/*.json");
    for (final String fileContents : files) {
      final Document doc = Document.parse(fileContents);
      final MongoCollection<Document> collection = db.getCollection("flow_task_templates");
      collection.insertOne(doc);

    }
  }

  @ChangeSet(order = "005", id = "005", author = "Adrienne Hudson")
  public void updateFlowTemplates(MongoDatabase db) throws IOException {
    final MongoCollection<Document> modesCollection = db.getCollection("flow_task_templates");

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
      final MongoCollection<Document> collection = db.getCollection("flow_task_templates");
      collection.insertOne(doc);

    }
  }

  @ChangeSet(order = "007", id = "007", author = "Adrienne Hudson")
  public void addPlatformNotificationTemplate(MongoDatabase db) throws IOException {

    final List<String> files = fileloadingService.loadFiles("flow/007/flow_task_templates/*.json");
    for (final String fileContents : files) {
      final Document doc = Document.parse(fileContents);
      final MongoCollection<Document> collection = db.getCollection("flow_task_templates");
      collection.insertOne(doc);

    }
  }

  @ChangeSet(order = "008", id = "008", author = "Adrienne Hudson")
  public void updateFlowTaskTemplates(MongoDatabase db) throws IOException {

    db.getCollection("flow_task_templates").deleteOne(eq("name", "Make Repositories Private"));
    db.getCollection("flow_task_templates")
        .deleteOne(eq("name", "Find Public Repositories in Org"));
    db.getCollection("flow_task_templates").deleteOne(eq("name", "Update Incidents"));
    db.getCollection("flow_task_templates").deleteOne(eq("name", "Get Incidents"));

    final List<String> files = fileloadingService.loadFiles("flow/008/flow_task_templates/*.json");
    for (final String fileContents : files) {
      final Document doc = Document.parse(fileContents);
      final MongoCollection<Document> collection = db.getCollection("flow_task_templates");
      collection.insertOne(doc);

    }
  }

  @ChangeSet(order = "009", id = "009", author = "Adrienne Hudson")
  public void updateFlowTaskTemplateOptions(MongoDatabase db) throws IOException {

    final MongoCollection<Document> flowTaskTemplateCollection =
        db.getCollection("flow_task_templates");
    final FindIterable<Document> flowTaskTemplates = flowTaskTemplateCollection.find();
    for (final Document flowTaskTemplate : flowTaskTemplates) {

      List<Document> configs = (List<Document>) flowTaskTemplate.get("config");
      if (configs != null) {
        for (Document config : configs) {

          List<String> options = (List<String>) config.get("options");
          if (options != null) {

            List<Document> newOptions = new ArrayList<Document>();
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
        db.getCollection("flow_task_templates");
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

    final MongoCollection<Document> flowWorkflowsCollection = db.getCollection("flow_workflows");
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
        db.getCollection("flow_task_templates");

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
    final MongoCollection<Document> templateCollection = db.getCollection("flow_task_templates");

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
    final MongoCollection<Document> templateCollection = db.getCollection("flow_task_templates");

    Bson update1 = Updates.set("name", "Send Platform Email");

    Bson query1 = Filters.eq("name", "Send Email");
    templateCollection.findOneAndUpdate(query1, update1);
  }

  @ChangeSet(order = "015", id = "015", author = "Adrienne Hudson")
  public void addTaskTemplate(MongoDatabase db) throws IOException {

    final List<String> files = fileloadingService.loadFiles("flow/009/flow_task_templates/*.json");
    for (final String fileContents : files) {
      final Document doc = Document.parse(fileContents);
      final MongoCollection<Document> collection = db.getCollection("flow_task_templates");
      collection.insertOne(doc);

    }
  }

  @ChangeSet(order = "016", id = "016", author = "Marcus Roy")
  public void updateTemplates(MongoDatabase db) throws IOException {

    BasicDBObject document = new BasicDBObject();
    final MongoCollection<Document> collection = db.getCollection("flow_task_templates");
    collection.deleteMany(document);

    final List<String> files = fileloadingService.loadFiles("flow/016/flow_task_templates/*.json");
    for (final String fileContents : files) {
      final Document doc = Document.parse(fileContents);

      collection.insertOne(doc);
    }
  }

  @ChangeSet(order = "017", id = "017", author = "Adrienne Hudson")
  public void flowTaskTemplateUpdate(MongoDatabase db) throws IOException {

    db.getCollection("flow_task_templates").deleteOne(eq("name", "Execute Shell"));
    db.getCollection("flow_task_templates").deleteOne(eq("name", "Switch"));
    db.getCollection("flow_task_templates").deleteOne(eq("name", "Send Rick Slack Message"));
    db.getCollection("flow_task_templates").deleteOne(eq("name", "Send Simple Slack Message"));

    final List<String> files = fileloadingService.loadFiles("flow/017/flow_task_templates/*.json");
    for (final String fileContents : files) {
      final Document doc = Document.parse(fileContents);
      final MongoCollection<Document> collection = db.getCollection("flow_task_templates");
      collection.insertOne(doc);

    }
  }

  @ChangeSet(order = "018", id = "018", author = "Adrienne Hudson")
  public void addNewTaskTemplates(MongoDatabase db) throws IOException {

    final List<String> files = fileloadingService.loadFiles("flow/018/flow_task_templates/*.json");
    for (final String fileContents : files) {
      final Document doc = Document.parse(fileContents);
      final MongoCollection<Document> collection = db.getCollection("flow_task_templates");
      collection.insertOne(doc);

    }
  }

  @ChangeSet(order = "019", id = "019", author = "Adrienne Hudson")
  public void addNewTaskTemplate(MongoDatabase db) throws IOException {

    final List<String> files = fileloadingService.loadFiles("flow/019/flow_task_templates/*.json");
    for (final String fileContents : files) {
      final Document doc = Document.parse(fileContents);
      final MongoCollection<Document> collection = db.getCollection("flow_task_templates");
      collection.insertOne(doc);

    }
  }

  @ChangeSet(order = "020", id = "020", author = "Adrienne Hudson")
  public void updateFlowTaskTemplate(MongoDatabase db) throws IOException {

    db.getCollection("flow_task_templates").deleteOne(eq("name", "Send Platform Email"));
    db.getCollection("flow_task_templates").deleteOne(eq("name", "Send Slack Log Message"));


    final List<String> files = fileloadingService.loadFiles("flow/020/flow_task_templates/*.json");
    for (final String fileContents : files) {
      final Document doc = Document.parse(fileContents);
      final MongoCollection<Document> collection = db.getCollection("flow_task_templates");
      collection.insertOne(doc);

    }
  }

  @ChangeSet(order = "021", id = "021", author = "Adrienne Hudson")
  public void addFlowTaskTemplate(MongoDatabase db) throws IOException {

    final List<String> files = fileloadingService.loadFiles("flow/021/flow_task_templates/*.json");
    for (final String fileContents : files) {
      final Document doc = Document.parse(fileContents);
      final MongoCollection<Document> collection = db.getCollection("flow_task_templates");
      collection.insertOne(doc);

    }
  }

  @ChangeSet(order = "023", id = "023", author = "Adrienne Hudson")
  public void taskTemplateUpdate(MongoDatabase db) throws IOException {

    db.getCollection("flow_task_templates").deleteOne(eq("name", "Execute HTTP Call"));
    db.getCollection("flow_task_templates").deleteOne(eq("name", "Artifactory File Upload"));


    final List<String> files = fileloadingService.loadFiles("flow/023/flow_task_templates/*.json");
    for (final String fileContents : files) {
      final Document doc = Document.parse(fileContents);
      final MongoCollection<Document> collection = db.getCollection("flow_task_templates");
      collection.insertOne(doc);

    }
  }

  @ChangeSet(order = "024", id = "024", author = "Adrienne Hudson")
  public void addTwilloFlowTaskTemplate(MongoDatabase db) throws IOException {

    final List<String> files = fileloadingService.loadFiles("flow/024/flow_task_templates/*.json");
    for (final String fileContents : files) {
      final Document doc = Document.parse(fileContents);
      final MongoCollection<Document> collection = db.getCollection("flow_task_templates");
      collection.insertOne(doc);

    }
  }


  @ChangeSet(order = "025", id = "025", author = "Adrienne Hudson")
  public void verifyFlowTaskTemplates(MongoDatabase db) throws IOException {
    final MongoCollection<Document> flowTaskTemplateCollection =
        db.getCollection("flow_task_templates");

    final FindIterable<Document> flowTemplates = flowTaskTemplateCollection.find();
    for (final Document flowTemplate : flowTemplates) {
      flowTemplate.put("verified", true);
      flowTaskTemplateCollection.replaceOne(eq("_id", flowTemplate.getObjectId("_id")),
          flowTemplate);
    }
  }

  @ChangeSet(order = "026", id = "026", author = "Adrienne Hudson")
  public void createFlowSettings(MongoDatabase db) throws IOException {
    db.createCollection("flow_settings");

    final List<String> files = fileloadingService.loadFiles("flow/026/flow_settings/*.json");
    for (final String fileContents : files) {
      final Document doc = Document.parse(fileContents);
      final MongoCollection<Document> collection = db.getCollection("flow_settings");
      collection.insertOne(doc);
    }
  }

  @ChangeSet(order = "027", id = "027", author = "Adrienne Hudson")
  public void updateFlowSetting(MongoDatabase db) throws IOException {

    db.getCollection("flow_settings").deleteOne(eq("name", "Workers"));


    final List<String> files = fileloadingService.loadFiles("flow/027/flow_settings/*.json");
    for (final String fileContents : files) {
      final Document doc = Document.parse(fileContents);
      final MongoCollection<Document> collection = db.getCollection("flow_settings");
      collection.insertOne(doc);

    }
  }

  @ChangeSet(order = "028", id = "028", author = "Adrienne Hudson")
  public void taskTemplateUpdatrs(MongoDatabase db) throws IOException {

    final MongoCollection<Document> collection = db.getCollection("flow_task_templates");
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

    final MongoCollection<Document> collection = db.getCollection("flow_task_templates");
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
    final MongoCollection<Document> collection = db.getCollection("flow_task_templates");
    collection.deleteMany(document);

    final List<String> files = fileloadingService.loadFiles("flow/030/flow_task_templates/*.json");
    for (final String fileContents : files) {
      final Document doc = Document.parse(fileContents);

      collection.insertOne(doc);
    }
  }

}
