# Legal RAG Dataset Governance

`cases.v1.jsonl` is the seed answer/retrieval dataset for Chinese residential-tenancy questions.

## Review states

- `source_verified`: the expected rule and article number were checked against an official government or judicial publication. This is not the same as review by a practicing lawyer.
- `legal_reviewed`: a qualified legal reviewer has checked the question, reference answer, applicability assumptions, and expected citations.

The seed set is `source_verified`. Before using results in an external quality claim, assign reviewers,
record the review date and jurisdiction, promote accepted cases to `legal_reviewed`, and version the dataset.

## Required fields

- Stable `id`, `datasetVersion`, and `category`
- `query`, `regionCode`, `asOfDate`, and retrieval `k`
- `expectedBehavior`: `answer`, `insufficient_evidence`, or `out_of_scope`
- `expectedSources`: title/article pairs that should support the answer
- `referenceAnswer` and minimal `requiredKeywords`
- `reviewStatus` and an `officialSource`

Cases must not contain personal data, secrets, real private contracts, or unverifiable legal conclusions.
Changes to expected answers or citations require a dataset-version increment and reviewer notes.
