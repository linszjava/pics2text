# Java relay photo upload

The operations upload page sends one authenticated multipart request per photo to
`POST /api/v1/projects/{projectId}/photos/relay-uploads`. Multipart resolution is lazy and servlet file/request
limits are finite, so the authentication interceptor runs before a file part is resolved. The service streams the
part to `photo-relay.temp-directory` while hashing it; it never materializes the photo as a `byte[]`.

After the local file is durable, one transaction creates the `photo_asset` and `photo_relay_upload_task`. The task
table is the queue source of truth. A fixed `photo-relay.concurrency` executor (default 4) uploads files with the S3
SDK's file-backed request body. Claims use a token and `FOR UPDATE SKIP LOCKED`; rejected executor submissions release
their claim, while active uploads heartbeat the lease. A worker that loses its token cannot finalize the task or delete
the staged file. Stale claims become `RETRY_WAIT`, uploads have finite exponential retries, and terminal tasks delete the
staged file. Existing verification, deduplication, short finalization transaction, audit, analysis job and Outbox paths
are reused unchanged.

Admission rejects when the configured pending-task or staged-byte budget is exhausted and checks filesystem usable
space before copying. Servlet multipart limits provide the earlier hard request/file ceiling. Operators should place
the servlet multipart directory and relay staging directory on a quota-controlled filesystem.

## Deployment limitation

Local staging is intentionally single-instance. `photo-relay.node-id` pins tasks to the node that owns their file and
supports restart recovery only when that node restarts with the same database, node ID and persistent temp volume.
It does not provide failover to another API instance. Multi-instance deployment requires shared durable staging or a
separate upload service; do not point multiple nodes at unrelated local disks with the same node ID.

The legacy presigned direct-upload endpoints remain compatible. They are not used by the current operations page.
