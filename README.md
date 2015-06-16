## flowthings.io Smart Agriculture Grails Demo

A simple smart-agriculture demo using [flowthings.io](https://flowthings.io) and Grails 2.4.x

To see it live, visit:

[http://grails-ag.mybluemix.net](http://grails-ag.mybluemix.net)

#### Self-deploy installation instructions
Add your flowthings.io account name and master token to the following file:
```
grails-app/controllers/flowthings/grails/demo/AgricultureController.groovy
```

Run the application:
```sh
grails run-app
```

#### IBM Bluemix installation instructions

*Note:* You may need to increase the app memory limit to 1GB if deployment failures occur

Build the war: 
```sh
grails war
```

Login to the Bluemix space:
```sh
cf login -u <ibm username> -o <organization name> -s <space name>
```

Create a flowthings.io service
```sh
cf create-service flowthings free flowthings
```

Push using the custom Tomcat buildpack
```sh
cf push grails-ag -p target/work/tomcat/webapps/flowthings-grails-demo-0.1.war -b https://github.com/cloudfoundry/java-buildpack.git -f manifest.yml
```

Follow the progress by tailing the logs (in another terminal):
```sh
cf logs grails-ag
```

Go to the bluemix dashboard, then navigate to the route given