# Glossary

Terms used in this project, in plain language. Add a term when you first meet it.

| Term | Meaning |
|---|---|
| **301 / 404** | HTTP status codes: 301 "moved permanently" (follow the new address), 404 "not found". Search engines rely on them. |
| **Caddy** | A web server that obtains and renews HTTPS certificates automatically. One option for the HTTPS proxy in front of Nginx. |
| **Canonical link** | A tag naming the one official address of a page, so search engines do not count copies (e.g. staging) separately. |
| **noindex** | A tag asking search engines not to list a page. Staging uses it on every page. |
| **Reverse proxy** | A server that receives visitors' requests and passes them to another server (HTTPS proxy → Nginx → API). |
| **ADR** | Architecture Decision Record: a short document explaining one significant decision and why. See [ADR index](../architecture/ADR/README.md). |
| **Artifact (CI)** | A file a CI job keeps after it finishes, such as test reports. |
| **CI / CD** | Continuous integration (automatically check every change) / continuous delivery (package and deploy the same way every time). See [CI/CD Pipeline](../operations/CI_CD_PIPELINE.md). |
| **Container / image** | An image is a packaged application with everything it needs to run; a container is a running copy of an image. |
| **CSRF** | Cross-site request forgery: another website tricking a signed-in browser into sending a request. Prevented with a token the other site cannot read. |
| **CVE** | A numbered public report of a security flaw in a specific piece of software, e.g. CVE-2026-65182. |
| **Droplet** | DigitalOcean's name for a virtual server. Ours: 1 vCPU, 1 GB RAM, Frankfurt. |
| **Flyway / migration** | Flyway applies numbered SQL files (`V1__…`, `V2__…`) in order to change the database schema. Each runs once and is never edited afterwards. |
| **GHCR** | GitHub Container Registry: storage for Docker images, next to the code. |
| **Lock file** | `package-lock.json`: records the exact version of every installed package so every install is identical. |
| **Runner** | The temporary machine GitHub provides to run a CI job. It is discarded afterwards. |
| **SSI** | Server Side Includes: Nginx inserting content (here, per-page titles and descriptions from the API) into HTML before sending it. Why the frontend is not on a static host ([ADR-006](../architecture/ADR/ADR-006-serve-frontend-from-nginx.md)). |
| **Swap / kswapd** | Swap is disk space used as overflow memory (much slower than RAM). `kswapd` is the kernel process that frees memory; high CPU use by it means memory has run out. |
| **Testcontainers** | A library that starts a real, throwaway database in Docker for tests, then removes it. |
| **Transitive dependency** | A library you did not add yourself but that one of your dependencies needs (e.g. Tomcat via Spring Boot). |
| **Trivy** | A scanner that checks dependencies against vulnerability databases and finds secrets in files. |
| **Cache-Control** | A response header telling browsers how long they may reuse a file without asking the server again. See [Performance](../operations/PERFORMANCE.md). |
| **Code splitting** | Building the frontend as several scripts, so a page downloads only the code it needs; the rest loads when first visited. |
| **Content hash** | A short code computed from a file's contents and put in its name (`index-D9YZY2KH.js`). Any change gives a new name, so old copies can be cached forever. |
| **gzip** | Compression the server applies to text files before sending them; the browser unpacks them. Our main script goes from 328 KB to about 100 KB. |
| **Idempotency key** | A random ID sent with a form so the server can recognise a repeat: sending the same request twice (for example after a lost reply) stores it once. A changed request needs a new key, or the server answers with the old one. |
| **Lazy loading** | Fetching an image or script only when it is about to be needed, instead of with the page. |
| **LCP / FCP / TBT** | Lighthouse timings: when the largest element appears (Largest Contentful Paint), when anything appears (First Contentful Paint), and how long scripts block clicks and typing (Total Blocking Time). |
| **Lighthouse** | Google's tool that loads a page in Chrome under simulated slow conditions and scores its speed. A lab test, not real visitors' data. |
| **Tap target** | The area of a button or link that responds to a finger. WCAG 2.5.8 asks for at least 24 × 24 CSS pixels. |
| **WCAG** | Web Content Accessibility Guidelines: the standard for making websites usable by people with disabilities (contrast, keyboard use, target size and more). |
