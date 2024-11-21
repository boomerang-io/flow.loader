FROM eclipse-temurin:17-jdk-alpine
ARG BMRG_TAG
ENV JAVA_OPTS=""
ENV BMRG_HOME=/opt/boomerang
ENV BMRG_SVC=loader-$BMRG_TAG

WORKDIR $BMRG_HOME
ADD target/$BMRG_SVC.jar service.jar
RUN sh -c 'touch /service.jar'
RUN apk add --upgrade expat

# Create user, chown, and chmod. 
# OpenShift requires that a numeric user is used in the USER declaration instead of the user name
RUN chmod -R u+x $BMRG_HOME \
    && chgrp -R 0 $BMRG_HOME \
    && chmod -R g=u $BMRG_HOME
USER 2000
#Add Health check instruction
HEALTHCHECK NONE
ENTRYPOINT [ "sh", "-c", "java $JAVA_OPTS -Djava.security.egd=file:/dev/./urandom -jar ./service.jar" ]
