# service-deployments

[![Build Status](https://travis-ci.org/hmrc/service-deployments.svg?branch=master)](https://travis-ci.org/hmrc/service-deployments) [ ![Download](https://api.bintray.com/packages/hmrc/deployments/service-deployments/images/download.svg) ](https://bintray.com/hmrc/deployments/service-deployments/_latestVersion)

This service provides information about recent deployments across the platform.  
Used by the cataologue-frontend service deployments page.

#### Local development
If running this locally for development, for example whilst running the catalogue-frontend, you will need to alter the config to point to the releases endpoint service directly.
The produciton config uses the releases.public.mdtp dns entry which will not work locally.  
Use https://releases.tax.service.gov.uk/ instead.

Also, if the data is to be retrieved and inserted into Mongo the scheduler needs to be enabled. This can be done by adding a line to the application.conf file;
> scheduler.enabled=true

You will also need to use your own github user and access token.
So all the changes required in application.conf are:

````
deployments.api.url = "https://releases.tax.service.gov.uk/"
scheduler.enabled=true
github.open.api.user="YOUR_GITHUB.COM_USER"
git.open.api.token = "YOUR_GITHUB_DEVELOPER_PERSONAL_ACCESS_TOKEN"
````

### License

This code is open source software licensed under the [Apache 2.0 License]("http://www.apache.org/licenses/LICENSE-2.0.html")
