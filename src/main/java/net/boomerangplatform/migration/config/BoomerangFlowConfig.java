package net.boomerangplatform.migration.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import com.github.cloudyrock.mongock.Mongock;
import com.github.cloudyrock.mongock.MongockBuilder;
import com.mongodb.MongoClient;
import com.mongodb.MongoClientURI;
import net.boomerangplatform.migration.BoomerangMigration;
import net.boomerangplatform.migration.SpringContextBridge;

@Configuration
@Profile("flow")
public class BoomerangFlowConfig implements BoomerangMigration {

  private final Logger logger = LoggerFactory.getLogger(BoomerangFlowConfig.class);

  @Value("${spring.data.mongodb.uri}")
  private String mongodbUri;
  
  private static String collectionPrefix;
  
  public BoomerangFlowConfig() {
    collectionPrefix = SpringContextBridge.services().getCollectionPrefix();
  }

  @Override
  public Mongock mongock() {

    logger.info("Creating MongoDB Configuration for: Flow");
    logger.info("MongoDB Uri: {}", mongodbUri);

    MongoClientURI uri = new MongoClientURI(mongodbUri);
    MongoClient mongoclient = new MongoClient(uri);

    MongockBuilder mongockBuilder = new MongockBuilder(mongoclient, uri.getDatabase(),
        "net.boomerangplatform.migration.changesets.flow");
    mongockBuilder.setChangeLogCollectionName(collectionPrefix + "sys_changelog_flow");
    mongockBuilder.setLockCollectionName(collectionPrefix + "sys_lock_flow");

    return mongockBuilder.setLockQuickConfig().build();
  }
}
