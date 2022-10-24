# Flow Loader


Every migration starts with creating a ChangeSet (annotated with @ChangeSet). It contains the following attributes:


|Attribute|  Description| Mandatory? |
|--|--|--|
| id | Returns the ChangeSet's id that will be stored in the ChangeSet history table/collection and will the way to identify a ChangeSet. The combination of this field and the author must be unique among the changesets. |Yes |
| order |   Returns the ChangeSet's execution order.|Yes |
| author | Returns the ChangeSet's author. The combination of this and the author must be unique among the changesets. |No |


## Creating Collections

https://www.mongodb.com/docs/manual/reference/method/js-database/

To create a collection, use the `db.createCollection()` method. 

```
    db.createCollection(collectionPrefix + "settings");
```

## Adding Documents to Collections
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

Updating documents in a collection and involve modifying and/or removing documents. 

