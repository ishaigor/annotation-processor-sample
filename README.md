# annotation-processor-sample
Gradle 5 issue sample

## To see it working with project versions 4.10.2 and 5.0
* Set gradle version to {4.10.2, 5.0} in gradle/wrapper/gradle-wrapper.properties
* In client/build.gradle pick
    * Comment ``annotationProcessor "com.company:model-processor:${project.version}"``
    * Uncomment ``annotationProcessor project(':model-processor')``
* Run in root directory ```./gradlew clean && ./gradlew build```
* You should see 
    * client/build/generated/source/apt/main/com/company/client/ImmutableClient.java
    * client/build/generated/source/apt/main/com/company/client/ImmutableObjectModule.java

## To see it working with local artifact versions 4.10.2 and 5.0
* Make it work with project first as above
* Set gradle version to {4.10.2, 5.0} in gradle/wrapper/gradle-wrapper.properties
* Run in root directory ``gw publishToMavenLocal`` to publish artifacts to local repo
* In client/build.gradle
    * Uncomment ``annotationProcessor "com.company:model-processor:${project.version}"``
    * Comment ``annotationProcessor project(':model-processor')``
* Run in root directory ```./gradlew clean && ./gradlew build```
* You should see only 
    * client/build/generated/source/apt/main/com/company/client/ImmutableClient.java
    * client/build/generated/source/apt/main/com/company/client/ImmutableObjectModule.java

