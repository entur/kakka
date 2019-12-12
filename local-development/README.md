# Local development

Note: This is a work-in-progress setup for local development of kakka.

## Howto

1. Copy the application.properties file to the project root
2. Apply the patch file `local-development.patch` using IntelliJ
3. Use docker-compose in current folder to run postgres, activemq and elasticsearch:
    `GCS_LOCAL_STORAGE=/path/to/local/storage/folder docker-compose up`
4. Run no.entur.kakka.test.TestApp with the application.properties from step 1.
    - It requires 
        - the maven task `test-compile` in order to copy the pubsub emulator jar to the target folder.
        - env variable GCS_LOCAL_STORAGE set to /path/to/local/storage/folder          

