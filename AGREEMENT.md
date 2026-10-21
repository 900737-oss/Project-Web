# Project Agreement — Group H

**Due:** 2 October 2026.

> Rules are in [`Group Project`](https://moodle.unizar.es/add/course/section.php?id=1702745).  
> Feature cards are in [`docs/features/`](docs/features/).  
> This file is the cover sheet: one fact, one place.  


## Features

The three seed features (create short URL, redirect and click log, link stats) are provided. Do not list them here.

Each row below is a **grown** feature: a card copied from the Feature Catalogue, or a new card in that same shape, plus a chosen weight. Personal names and ownership belong in `TEAM.md`, not here.

| Feature card | Owner module | Weight | Event-coupled |
| --- | --- | --- | --- |
| [docs/features/0004-link_preview_open_graph.md](docs/features/0004-link_preview_open_graph.md) | preview | 21 | Y |
| [docs/features/0005-csv-upload.md](docs/features/0005-csv-upload.md) | links | 13 | Y |
| [docs/features/0006-redirect_cache_cross_replica.md](docs/features/0006-redirect_cache_cross_replic.md) | links | 21 | Y |
| [docs/features/0007-qr_code.md](docs/features/0007-qr_code.md) | links | 8 | N |
| [docs/features/0008-async_click_ingestion.md](docs/features/0008-async_click_ingestion.md) | 21 | clicks | Y |

## Budget

| Rule | Value | Status |
| --- | --- | --- |
| Σ weights | **= 84** | Yes |
| Grown features | **≥ 4** (one per member) | Yes |
| Event-coupled | **≥ 2** | Yes |

## Commitment

We commit to:

- Implementing the feature cards listed above and keeping them up to date.
- Each member being able to explain and demo the feature they own at the oral defence.
- Maintaining CI green and `docker compose up` working throughout the project.
- Disclosing AI assistance on each feature card before grading.

By pushing this file we confirm the above commitment.
