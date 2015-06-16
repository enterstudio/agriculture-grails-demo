## flowthings.io Smart Agriculture Grails Demo

A simple smart-agriculture demo using [flowthings.io](https://flowthings.io) and Grails 2.4.x

To see it live, visit:

[http://grails-ag.mybluemix.net](http://grails-ag.mybluemix.net)

#### Self-deploy installation instructions
1. Add your flowthings.io account name and master token to the following file:
```
grails-app/controllers/flowthings/grails/demo/AgricultureController.groovy
```
2. Run the application:
```sh
grails run-app
```

#### IBM Bluemix installation instructions

*Note:* You may need to increase the app memory limit to 1GB if deployment failures occur

1. Don't create an app on Bluemix itself! We will do this during the `cf push` phase.
2. Build the war: 
```sh
grails war
```
3. Login to the Bluemix space:
```sh
cf login -u <ibm username> -o <organization name> -s <space name>
```
4. Create a flowthings.io service
```sh
cf create-service flowthings free flowthings
```
5. Push using the custom Tomcat buildpack
```sh
cf push grails-ag -p target/work/tomcat/webapps/flowthings-grails-demo-0.1.war -b https://github.com/cloudfoundry/java-buildpack.git -f manifest.yml
```
6. Follow the progress by tailing the logs (in another terminal):
```sh
cf logs grails-ag
```
7. Go to the bluemix dashboard, then navigate to the route given