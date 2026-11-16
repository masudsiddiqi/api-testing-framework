# What changed compared with the dual-mode framework (mock + real)

Removed (the Prism mock support):
- `env` switch and `baseUri.prism` / `baseUri.real` -> one key: `baseUri`
- `ConfigUtils`: `ENV_MOCK`, `ENV_REAL`, `getEnv()`, `isMockServer()`; placeholder defaults for accounts -> every value is required
- `HeaderFactory`: `addPreferHeaderIfMock` and the 5 `...AndMockPreferredCode` methods
- `ExpectedStatusCode`: `mockStatus`, `forMockAndReal`, `forRealOnly`, `hasMockStatus`, `resolve` -> `status` + `expect(status, policy)`
- `SchemaAndAllowHeaderValidationPolicy`: `SchemaScope`, `validateSchemaOnMockOnly/RealOnly`, `validatesSchemaOn`;
  `validateSchemaOnBoth` renamed `validateSchemaOnly`
- `ResponseValidator`: the `isMockServer` parameter and all mock branches (Allow header always hard, schema mismatch always soft failure)
- `BaseTest`: the Prism malformed-JSON timeout workaround
- `LoginNegativeTests` / `LoginNegativeDataProvider`: the `isMock` column; the test always expects 401
- the Prism comments and `prism-status-code.txt`

Additions and fixes that are NOT just removals (check them):
- `PostBookingDataProvider`: row 5.2 (duplicate) removed, it is `BookingNegativeTests.testPostDuplicateBookingIsRejected`
- `PostBookingDataProvider`: `uniqueValidBookingPayloadPOJO()` for the two happy paths (unique lastname)
- `PutBookingDataProvider`: two empty `{}` rows removed; endpoints get a leading `/`; method is `"PUT"`
- the swapped "Layer 1A / 1B" comment lines in `PostBookingDataProvider` corrected
- `testng.xml`: `LoginNegativeTests` added
- `BookingWorkflowTests`: the Prism paragraph replaced by a status note (the tests are still `enabled = false` except one)
- `HeaderUtils_MethodList.txt`, `README.md` rewritten

Not carried over: the HTML documents (README.html, README_bn.html, the two diagrams) still describe the dual-mode
framework; the bundled Allure tool, `target`, `allure-results`, `test-output`.
