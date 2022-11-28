# Flow Loader

> Note: this service only works with Java 11

Every migration starts with creating a ChangeSet (annotated with @ChangeSet). It contains the following attributes:


|Attribute|  Description| Mandatory? |
|--|--|--|
| id | Returns the ChangeSet's id that will be stored in the ChangeSet history table/collection and will the way to identify a ChangeSet. The combination of this field and the author must be unique among the changesets. |Yes |
| order |   Returns the ChangeSet's execution order.|Yes |
| author | Returns the ChangeSet's author. The combination of this and the author must be unique among the changesets. |No |


## ChangeLog and Lock Collections

The changelog and lock collections are defined in `/loader/src/main/java/net/boomerangplatform/migration/config/BoomerangFlowConfig.java`


## Creating Collections

https://www.mongodb.com/docs/manual/reference/method/js-database/

To create a collection, use the `db.createCollection()` method. 

```
    db.createCollection(collectionPrefix + "settings");
```

## Adding Documents to Collections
https://www.mongodb.com/docs/manual/reference/method/js-collection/

In `/loader/src/main/resources/flow`, there are several numbered folders. The numbering for the folder matches the changeset in which it is applied. 
 
For example,

```
  @ChangeSet(order = "026", id = "026", author = "Adrienne Hudson")
  public void createFlowSettings(MongoDatabase db) throws IOException {
    db.createCollection(collectionPrefix + "settings");

    final List<String> files = fileloadingService.loadFiles("flow/026/flow_settings/*.json");
    for (final String fileContents : files) {
      final Document doc = Document.parse(fileContents);
      final MongoCollection<Document> collection = db.getCollection(collectionPrefix + "settings");
      collection.insertOne(doc);
    }
  }
```
where the documents added with changeset 026 are contained within the `flow/026/flow_settings/` folder in `/loader/src/main/resources/`


## Updating Documents in a Collection
https://www.mongodb.com/docs/manual/reference/method/js-collection/

Modifying existing documents requires identification of the document(s). 

```
  @ChangeSet(order = "064", id = "064", author = "Adrienne Hudson")
  public void updateWorker(MongoDatabase db) throws IOException {
    MongoCollection<Document> collection = db.getCollection(collectionPrefix + "settings");
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
```
In this example, we query to find the first document in the `collectionPrefix + "settings` collection with the `"name": "Task Configuration"` with the statement `Document workers = collection.find(eq("name", "Task Configuration")).first();`. Once the document has been identified, the config with `"key":"worker.image"` is updated with `"value":"boomerangio/worker-flow:2.8.11"`
and the update is written back onto the document. 

https://mongodb.github.io/mongo-java-driver/3.4/javadoc/org/bson/Document

```
  @ChangeSet(order = "073", id = "073", author = "Adrienne Hudson")
  public void migrateEnablePersistentStorage(MongoDatabase db) throws IOException {

    final MongoCollection<Document> flowWorkflowsCollection =
        db.getCollection(collectionPrefix + "workflows");
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
  ```
  In this example, the update applies to every document in the `collectionPrefix + "workflows"` collection. By using the `org.bson.Document.put(String key, Object value)` and `org.bson.Document.remove(Object key)`, object can be created, updated, or removed from a document.  

## Removing Documents in a Collection
https://www.mongodb.com/docs/manual/reference/method/js-database/

To remove a document in a collection, use the `db.collection.findOneAndDelete()` method. 

```
  @ChangeSet(order = "108", id = "108", author = "Adrienne Hudson")
  public void removeTemplates(MongoDatabase db) throws IOException {
    MongoCollection<Document> collection = db.getCollection(collectionPrefix + "workflows");
    collection.findOneAndDelete(eq("name", "Looking through planets with HTTP Call "));
    collection.findOneAndDelete(eq("name", "MongoDB email query results"));
  }
```

## How to Run

### Run Local MongoDB w Docker

```
docker run --name local-mongo -d mongo:latest
```

### CLI

```
mvn clean package

java -Dspring.profiles.active={profile} -jar target/loader.jar
```

If you are running a higher version of Java, you will need to locate the Java 11 version by running

```
/usr/libexec/java_home -V
```

Using the output you can then run the particular version, such as

```
/Library/Java/JavaVirtualMachines/ibm-semeru-open-11.jdk/Contents/Home/bin/java -Dspring.profiles.active=flow -jar target/loader.jar
```

### Docker

```
docker run -e JAVA_OPTS="-Dspring.data.mongodb.uri=mongodb://localhost:27017/boomerang -Dflow.mongo.collection.prefix=flow -Dspring.profiles.active=flow" --network host --platform linux/amd64 boomerangio/flow-loader:latest
```
