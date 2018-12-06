# annotation-processor-sample
Gradle 5 issue sample

##To see it working with 4.10.2
* Switch gradle version to 4.10.2 in gradle/wrapper/gradle-wrapper.properties
* Run in root directory ```./gradlew clean && ./gradlew build```
* You should see 
    * client/build/generated/source/apt/main/com/company/client/ImmutableClient.java
    * client/build/generated/source/apt/main/com/company/client/ImmutableObjectModule.java

##To see it non-working with 5.0
* Switch gradle version to 5.0 in gradle/wrapper/gradle-wrapper.properties
* Run in root directory ```./gradlew clean && ./gradlew build```
* You should see only 
    * client/build/generated/source/apt/main/com/company/client/ImmutableClient.java

##To see it non-working with both versions 4.10.2 and 5.0
* Set gradle version to {4.10.2, 5.0} in gradle/wrapper/gradle-wrapper.properties
* Run in root directory ``gw publishToMavenLocal`` to publish artifacts to local repo
* In client/build.gradle
    * Uncomment ``annotationProcessor "com.company:model-processor:${project.version}"``
    * Comment ``annotationProcessor project(':model-processor')``
* Run in root directory ```./gradlew clean && ./gradlew build```
* You should see only 
    * client/build/generated/source/apt/main/com/company/client/ImmutableClient.java

