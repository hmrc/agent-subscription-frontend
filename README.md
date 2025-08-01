# agent-subscription-frontend

[![Build Status](https://travis-ci.org/hmrc/agent-subscription-frontend.svg)](https://travis-ci.org/hmrc/agent-subscription-frontend)

This is a frontend service for agent-subscription. After signing in using government-gateway, agents can go through the steps to
register for Agent Services and obtain an HMRC-AS-AGENT enrolment, giving them access a range of functions available for interacting
with their clients. The domain is Subscriptions to Agent Services 
following the ROSM (Register Once Subscribe Many) pattern.

For an accountant/bookkeeper to be eligible to register for an Agent Services Account:
- AMLS registered (or pending if via HMRC)
- Create a clean Government Gateway account with affinity type "Agent" to create new EACD group (this can be done during the journey if not starting with one)

We use the agent-assurance service for some of these checks

## Journey overview

Pre-task list:
- Business type
- UTR
- Postcode/NI or CRN depending on business type
- Vat registered? Gather details if yes.

Task list:
- AMLS (can be autocompleted if 'manually assured')
- contact details
- mapping (optional - if on unclean cred)
- create new GG (if on unclean cred)
- Check your answers

After submission the agent should be assigned the HMRC-AS-AGENT enrolment if successful. They can then access the Agent Services Account.

### Running the tests

    sbt test it/test
    
### Running the tests with coverage

    sbt clean coverageOn test it/test coverageReport

### Automated testing
This service is tested by the following automated test repositories:
- [agent-onboarding-ui-tests](https://github.com/hmrc/agent-onboarding-ui-tests)
- [agent-subscription-performance-tests](https://github.com/hmrc/agent-subscription-performance-tests)

### Running the app locally

    sm2 --start AGENT_ONBOARDING -r
    sm2 --stop AGENT_SUBSCRIPTION_FRONTEND
    sbt run
    
It should then be listening on port 9437

    browse http://localhost:9437/agent-subscription/start  
    
Alternatively use task list functionality:

    browse http://localhost:9437/agent-subscription/task-list  

## Continue URL

Agent Subscription journey can be integrated as part of external journey using `continue` url
parameter:
```
http://www.tax.service.gov.uk/agent-subscription/start?continue=/your-service/path?paramA=valueA
```
After successful subscription user will be redirected to Agent Services Account page and presented with `Continue with your journey` button.

### License 

This code is open source software licensed under the [Apache 2.0 License]("http://www.apache.org/licenses/LICENSE-2.0.html").
