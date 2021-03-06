# service-deployments

[![Build Status](https://travis-ci.org/hmrc/service-deployments.svg?branch=master)](https://travis-ci.org/hmrc/service-deployments) [ ![Download](https://api.bintray.com/packages/hmrc/deployments/service-deployments/images/download.svg) ](https://bintray.com/hmrc/deployments/service-deployments/_latestVersion)

This service provides information about recent deployments across the platform.  
Used by the cataologue-frontend service deployments page.

#### Local development
If running this locally for development, for example whilst running the catalogue-frontend, you will need to alter the config to point to the releases endpoint service directly.
The produciton config uses the releases.public.mdtp dns entry which will not work locally.  
Use the fully qualified url instead - check with platops if you do not know this url.

Also, if the data is to be retrieved and inserted into Mongo the scheduler needs to be enabled. This can be done by adding a line to the application.conf file;
> scheduler.enabled=true

You will also need to specify the base url to the artifactory server 
So all the changes required in application.conf are:

````
deployments.api.url = "FULLY_QUALIFIED_RELEASES_URL"
artifactory.url = "FULLY_QUALIFIED_ARTIFACTORY_URL"
scheduler.enabled=true
````

### License

This code is open source software licensed under the [Apache 2.0 License]("http://www.apache.org/licenses/LICENSE-2.0.html")
