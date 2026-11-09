# Files and resources

Phase 3 provides instructor uploads, external lesson links, lesson resource
ordering and expectations, course thumbnails, and private object storage.
MySQL stores resource metadata and lifecycle state; S3-compatible storage holds
the file bytes.

## Upload and processing lifecycle

1. Create a lesson upload with `POST
   /api/v1/instructor/lessons/{lessonId}/resources/uploads` or a course
   thumbnail upload with `POST
   /api/v1/instructor/courses/{courseId}/thumbnail/uploads`.
2. Upload the file directly to the returned `upload.url` using the returned
   method and every returned header. Do not send the backend bearer token to
   the object-storage URL.
3. Confirm using `POST /api/v1/instructor/resources/{resourceId}/confirm`.
   The backend verifies the stored object size and processes the upload
   asynchronously. Poll `GET /api/v1/instructor/resources/{resourceId}` until
   its status is `READY` or `FAILED`.
4. Downloads use `GET
   /api/v1/instructor/resources/{resourceId}/download-url`, which returns a
   short-lived signed URL for ready objects. External links return their
   validated HTTP(S) URL.

The API validates extension and declared MIME type before issuing an upload
URL, then checks actual content signatures and calculates SHA-256 while
streaming. OOXML extraction and processing concurrency are bounded. Failed
uploads return a stable reason code and are never offered as downloadable
content. The lifecycle and integrity checks are applied to thumbnails too.

## Instructor endpoints

All endpoints below require authentication and ownership of the associated
course. Instructor UI mode is presentation only and is not an authorization
grant.

| Method and path | Purpose |
| --- | --- |
| `GET /api/v1/instructor/lessons/{lessonId}/resources` | List non-deleted resources in display order |
| `POST /api/v1/instructor/lessons/{lessonId}/resources/links` | Add a validated HTTP(S) link |
| `PATCH /api/v1/instructor/resources/{resourceId}` | Rename a resource or change a link URL |
| `DELETE /api/v1/instructor/resources/{resourceId}` | Soft-delete a lesson resource |
| `PUT /api/v1/instructor/lessons/{lessonId}/resources/order` | Replace the exact current ordered ID set |
| `GET /api/v1/instructor/lessons/{lessonId}/expected-resources` | List expected resource declarations |
| `PUT /api/v1/instructor/lessons/{lessonId}/expected-resources` | Replace expected resource declarations |
| `GET /api/v1/instructor/resources/{resourceId}/download-url` | Get a short-lived download URL for a ready resource |
| `POST /api/v1/instructor/courses/{courseId}/thumbnail/uploads` | Start a thumbnail upload |
| `GET /api/v1/instructor/courses/{courseId}/thumbnail` | Get thumbnail lifecycle state and URL, if ready |
| `DELETE /api/v1/instructor/courses/{courseId}/thumbnail` | Remove the current thumbnail |

Expected resource types must be unique for a lesson. Minimum counts are 1–20;
labels are optional and limited to 80 characters. Lesson resources are ordered
from position zero without gaps. Reorder requests must provide every current
resource ID exactly once.

## Limits and reconciliation

The following environment variables bind to `peergeek.resources.*`; values are
defaults. Sizes are bytes, and durations use Spring duration notation.

| Variable | Default |
| --- | ---: |
| `PEERGEEK_RESOURCES_MAXPDFBYTES` | `52428800` |
| `PEERGEEK_RESOURCES_MAXDOCUMENTBYTES` | `52428800` |
| `PEERGEEK_RESOURCES_MAXIMAGEBYTES` | `10485760` |
| `PEERGEEK_RESOURCES_MAXTHUMBNAILBYTES` | `5242880` |
| `PEERGEEK_RESOURCES_MAXVIDEOBYTES` | `1073741824` |
| `PEERGEEK_RESOURCES_MAXRESOURCESPERLESSON` | `50` |
| `PEERGEEK_RESOURCES_PUTURLTTL` | `15m` |
| `PEERGEEK_RESOURCES_GETURLTTL` | `5m` |
| `PEERGEEK_RESOURCES_ABANDONEDUPLOADAGE` | `24h` |
| `PEERGEEK_RESOURCES_DELETEDRESOURCEGRACE` | `1h` |
| `PEERGEEK_RESOURCES_STUCKPROCESSINGAGE` | `15m` |
| `PEERGEEK_RESOURCES_CLEANUPINTERVAL` | `15m` |
| `PEERGEEK_RESOURCES_MAXPROCESSINGATTEMPTS` | `3` |
| `PEERGEEK_RESOURCES_PROCESSINGTHREADS` | `2` |
| `PEERGEEK_RESOURCES_PROCESSINGQUEUECAPACITY` | `32` |
| `PEERGEEK_RESOURCES_MAXOOXMLEXPANDEDBYTES` | `67108864` |
| `PEERGEEK_RESOURCES_ALLOWEDPDFMIMETYPES` | `application/pdf` |
| `PEERGEEK_RESOURCES_ALLOWEDDOCUMENTMIMETYPES` | OOXML, text/plain, and text/csv MIME allowlist |
| `PEERGEEK_RESOURCES_ALLOWEDIMAGEMIMETYPES` | PNG, JPEG, WebP, and GIF MIME allowlist |
| `PEERGEEK_RESOURCES_ALLOWEDVIDEOMIMETYPES` | MP4 and WebM MIME allowlist |
| `PEERGEEK_RESOURCES_ALLOWEDPDFMAPPINGS` | `pdf=application/pdf` |
| `PEERGEEK_RESOURCES_ALLOWEDDOCUMENTMAPPINGS` | `.docx`, `.pptx`, `.xlsx`, `.txt`, and `.csv` MIME mappings |
| `PEERGEEK_RESOURCES_ALLOWEDIMAGEMAPPINGS` | `.png`, `.jpg`, `.jpeg`, `.webp`, and `.gif` MIME mappings |
| `PEERGEEK_RESOURCES_ALLOWEDVIDEOMAPPINGS` | `.mp4` and `.webm` MIME mappings |

Scheduled reconciliation marks old pending uploads expired and soft-deleted,
re-dispatches stale processing records up to the configured attempt count, and
deletes stored objects for resources after their deletion grace period, and
removes sufficiently old objects under the application-owned `courses/` prefix
when no resource row references them. `DeleteObject` is idempotent, so a failed
cleanup can be retried on the next reconciliation interval. AWS credentials
used by the application need `s3:ListBucket` for startup verification and
orphan reconciliation in addition to the object read/write permissions.

## Object storage setup

Local Docker Compose uses MinIO with:

```dotenv
STORAGE_ENDPOINT=http://minio:9000
STORAGE_PUBLIC_ENDPOINT=http://localhost:9000
STORAGE_REGION=us-east-1
STORAGE_PATH_STYLE=true
STORAGE_CREATE_BUCKET_IF_MISSING=true
```

`STORAGE_ACCESS_KEY`, `STORAGE_SECRET_KEY`, and `STORAGE_BUCKET` are also
required. For real AWS S3, leave `STORAGE_ENDPOINT` and
`STORAGE_PUBLIC_ENDPOINT` empty, set `STORAGE_PATH_STYLE=false`, set
`STORAGE_REGION` and `STORAGE_BUCKET`, and set
`STORAGE_CREATE_BUCKET_IF_MISSING=false`. Static keys are optional but must be
provided together; otherwise the AWS SDK default credential provider chain is
used. Production rejects bucket auto-creation and performs a startup
`HeadBucket` check.

For browser access from `http://localhost:5173`, apply this CORS configuration
to the bucket:

```json
[
  {
    "AllowedOrigins": ["http://localhost:5173"],
    "AllowedMethods": ["PUT", "GET", "HEAD"],
    "AllowedHeaders": ["Content-Type", "Content-Length"],
    "ExposeHeaders": ["ETag"]
  }
]
```
