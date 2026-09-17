# Third-party notices

## Web preview

The browser preview uses [HLS.js](https://github.com/video-dev/hls.js) 1.7.3
for HLS playback in browsers that support MediaSource but do not provide
native HLS playback. HLS.js is licensed under the Apache License 2.0 and is
installed as a project-local dependency under `web/`.

## Native Android TV v2 / v3 and Android Mobile v1

The native TV information architecture, focus-navigation patterns, movie rows,
details layout, and Media3 player approach were designed with reference to
[JetStreamCompose](https://github.com/android/tv-samples/tree/main/JetStreamCompose)
from Google's Android TV samples, licensed under the Apache License 2.0.
The reference snapshot used during implementation was
`android/tv-samples@15ac8df7df91a07450751a5fe8c6fd17a6a4297f`.

[HomeFlix TV](https://github.com/azad25/homeflix-tv-app) was consulted for
cinematic hero and one-card-per-series continue-watching interaction ideas.
No HomeFlix source code is bundled in this project.

This project uses AndroidX Compose, AndroidX TV Material, AndroidX Media3,
Coil, OkHttp, and OkHttp DNS-over-HTTPS through their published Maven artifacts.
Their respective license metadata is retained in those artifacts. OkHttp and
its DNS-over-HTTPS module are maintained by Square under the Apache License 2.0.

[FongMi/TV](https://github.com/FongMi/TV) was evaluated as an architectural
reference only. No FongMi/TV source code, crawler configuration, or GPL-covered
implementation is bundled in this project.

## Legacy WebView v1

The inactive `app/` module keeps the earlier single-WebView implementation.
Its TV-oriented WebView structure, D-pad spatial-navigation approach, and
virtual-cursor approach were adapted from
[Autodarts-TV](https://github.com/TheJim03/Autodarts-TV), licensed under the MIT
License. Its upstream copyright notice remains in `LICENSE`.

This project does not bundle the referenced projects, site content, media
streams, third-party browser extensions, DRM bypasses, or access-control
bypasses.
