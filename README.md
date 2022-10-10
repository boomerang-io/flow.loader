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

## Updating Documents in a Collection
https://www.mongodb.com/docs/manual/reference/method/js-collection/

Updating documents in a collection and involve adding, modifying, and/or removing documents. 

