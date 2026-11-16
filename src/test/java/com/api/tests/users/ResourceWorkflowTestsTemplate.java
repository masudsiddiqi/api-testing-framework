package com.api.tests.users;


//PLACEHOLDER: this class has no tests. It shows how workflow tests are organized.
//
// A workflow test sends several requests in a row.
// The result of one request is the input of the next one
// (for example: create a resource, read it back, delete it, read it again).
// It checks that the state of the server stays correct across these requests.
//
// Workflow tests are grouped by RESOURCE, not by endpoint and not by HTTP method.
// One resource has one life cycle (create, read, update, delete).
// All the endpoints of that life cycle belong to ONE class.
// A new resource gets its own class, named <ResourceName>WorkflowTests, in its own package.
//
// A resource needs a workflow class only if requests CHANGE its state.
// A resource that can only be read (for example a list) does not need one.
// A check that needs only one request belongs to the data provider tests, not here.
//
// How to add a workflow class for a resource:
//	1. Copy an existing workflow class. Rename it to <ResourceName>WorkflowTests.
//	2. Change the endpoint, the payload and the helper methods.
//	3. Add the class to testng.xml.
public class ResourceWorkflowTestsTemplate {
	
}