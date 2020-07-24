package net.boomerangplatform.migration;

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.stereotype.Component;

@Component
public class SpringContextBridge implements SpringContextBridgedServices, ApplicationContextAware {

    private static ApplicationContext applicationContext;

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
        this.applicationContext = applicationContext;
    }
}