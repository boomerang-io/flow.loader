package io.boomerang.migration;

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.stereotype.Component;


@Component
public class SpringContextBridge implements SpringContextBridgedServices, ApplicationContextAware {

  private static ApplicationContext applicationContext;

  @Value("${flow.mongo.collection.prefix}")
  private String workflowCollectionPrefix;

  @Value("${flow.mongo.cosmosdbttl}")
  private boolean mongoCosmosDBTTL;

  public static SpringContextBridgedServices services() {
    return applicationContext.getBean(SpringContextBridgedServices.class);
  }

  @Autowired
  private FileLoadingService fileLoadingService;

  @Override
  public FileLoadingService getFileLoadingService() {
    return this.fileLoadingService;
  }

  @Override
  public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
    setContext(applicationContext);
  }

  private static synchronized void setContext(ApplicationContext context) {
    SpringContextBridge.applicationContext = context;
  }

  @Override
  public boolean getMongoCosmosDBTTL() {
    return mongoCosmosDBTTL;
  }
  
  @Override
  public String getCollectionPrefix() {

    if ("flow_".equals(workflowCollectionPrefix) || workflowCollectionPrefix == null
        || workflowCollectionPrefix.isBlank()) {
      return "";
    }
    return workflowCollectionPrefix.endsWith("_") ? workflowCollectionPrefix : workflowCollectionPrefix + "_";
  }
}
