# Measured workload results

1000 measured submissions: 600 engine mutations, 200 rejections, 200 exact retries; 100 measured fixtures and 100 measured reconnects. Two warm-up lifetimes are excluded. Failures 0; timeouts 0.

| Metric (ms) | Samples | p50 | p95 | Maximum |
|---|---:|---:|---:|---:|
| Accepted send → CPU authoritative render submission | 600 | 18.30 | 23.40 | 38.10 |
| Accepted send → result receipt | 600 | 5.20 | 6.60 | 22.10 |
| Reconnect → full snapshot + new render submission | 100 | 39.80 | 50.20 | 72.50 |

Snapshot payload bytes: n=600, p50=623, p95=623, max=623. Result payload bytes: n=1000, p50=146, p95=146, max=146. UTF-8 JSON payload only; WebSocket/TCP overhead excluded.

| Sample | JVM used heap MiB | JVM process RSS MiB | Container memory display | Chrome process working-set sum MiB | Chrome private-byte sum MiB | Home / away JS heap MiB |
|---|---:|---:|---|---:|---:|---|
| baseline-after-warmup | 80.86 | 230.01 | 217.2MiB / 7.412GiB | 1339.51 | 999.73 | 20.69 / 18.12 |
| lifetime-25 | 80.29 | 320.68 | 308.7MiB / 7.412GiB | 1435.92 | 1077.95 | 37.69 / 29.84 |
| lifetime-50 | 182.38 | 352.12 | 340MiB / 7.412GiB | 1411.54 | 1047.38 | 36 / 18.26 |
| lifetime-75 | 73.48 | 422.65 | 410.3MiB / 7.412GiB | 1383.78 | 1017.26 | 37.65 / 33.27 |
| lifetime-100 | 173.94 | 425.7 | 413.5MiB / 7.412GiB | 1439.71 | 1123.9 | 40.07 / 26.32 |
| idle-15s-retired | 176.76 | 425.71 | 413.8MiB / 7.412GiB | 1170.59 | 807.52 | 17.77 / 17.47 |

Memory sources are distinct. Summed process working sets can count shared pages more than once; private bytes are private committed memory, not private RSS. CDP page heaps exclude native/GPU allocations. Samples are collected serially while action submission is paused. Compare movement lifetime-25..100 for like-for-like growth; the final sample changes fixture type to Both Down and records retirement/idle without forced GC.

Machine: AMD Ryzen 7 260 w/ Radeon 780M Graphics, 16 logical CPUs, 15676.55 MiB RAM; win32 10.0.26200; Chrome 152.0.7977.77; Node v24.19.0. See the main report for pinned Java/Maven/container versions and limitations.

Raw: [workload.json](workload.json). Recomputed compact data: [summary.json](summary.json).
