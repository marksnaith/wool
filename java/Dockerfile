FROM tomcat:9

# add the tomcat users file
ADD tomcat-users.xml /usr/local/tomcat/conf/tomcat-users.xml

RUN cp -r /usr/local/tomcat/webapps.dist/manager /usr/local/tomcat/webapps/

ADD context.xml /usr/local/tomcat/webapps/manager/META-INF/context.xml

# add the build tools
ADD ./BuildTools /BuildTools

# add the WWS
ADD ./WoolWebService /WoolWebService

RUN chmod 777 /WoolWebService/gradlew

# run the build tool for the secret key and add it to the gradle properties
RUN sed "/woolconfigJwtSecretKey/s@SECRET@$(java -cp /BuildTools/BuildTools-1.0.0/buildtools-1.0.0.jar KeyGenerator -t base64 -s 1024)@" \
        /WoolWebService/gradle.sample.properties \
        > /WoolWebService/gradle.properties

WORKDIR /WoolWebService

RUN ./gradlew --stacktrace build

RUN cp ./build/libs/*.war /usr/local/tomcat/webapps/
