# Drools Analyzer library

## Concept
The main idea behind the library is as follows.
When you run Drools you need to supply some data (facts) for the rules to chew.
The problem is you don't have or don't want to prepare (request from external sources) all data in advance. Instead you 
may want to allow rules themselves decide which data to retrieve. 

Moreover, you may want to retrieve this data asynchronously and in non blocking manner.

So the library implements the flollowing loop:
```
while decision is not made:
 - run next drools iteration which may produce 
    - the decision or 
    - some data requests
 - request the data
 - inject the data into Drools for the next iteration
``` 
## Request data
In order to request data you need to implement `IDataRequestProcessor.IDataRequestProcessorReactive` 
interface which should implement a factory method pattern to provide a lambda to for each particular request *route*  
 
## Specific requirements 

Several corner cases need to be considered here:

- Data may be missing for instance due to error. 
In this case you specify where it's absolutely required or 
you may take a decision (specify rule) without it
- Data may come in the form of collection. 
In this case you specify whether you want to split the collection and insert into Drools each item
or insert the collection as a whole. 
- In very specific cases when different rules may produce concurrent decisions,
you may want to specify priority for them or run a special aggregation phase (denoted in rules as 
`AggregationPhase()` marker object) to separate Aggregation rules from normal ones. 
The rule executer will automatically run aggregation phase after any normal one
 injecting this object into Drools for this phase.
- When rules are complex - Audit log may help to understand how the decision was made
 ## Tests
Please see the tests for usage details and audit log example 
