# Flow Loader


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


