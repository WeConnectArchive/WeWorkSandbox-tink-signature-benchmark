# Tink Signature Micro-benchmark

This repository contains the code for a simple micro-benchmark of the
public key signature algorithms provided by Google's
[Tink](https://https://github.com/google/tink) cryptographic primitives
library.

## Benchmark Results

For those who don't care to run benchmarks themselves, here are some results
we prepared earlier.

Experiment parameters:

- Tink built from commit [`4bec791ccf1012d660188f62e086fbbd582722e9`](https://github.com/google/tink/commit/4bec791ccf1012d660188f62e086fbbd582722e9),
  committed 2018-03-30.
- 100 round warmup
- 1000 measured rounds, 1000 signing operations per round
- 59 randomized data input bytes per signing operation (similar to eBACS)
- Signing operations are run sequentially on a single thread.

The benchmark was run on an Amazon EC2 t2-micro instance. The results were as 
follows:

| Algorithm  | Median (ns) | Mean (ns) | Std dev. (ns) | Mean ops per second   |
|------------|-------------|-----------|---------------|-----------------------|
| ED25519    | 117860      | 117921    | 1692          | 8480                  |
| ECDSA_P256 | 1125607     | 1125284   | 4410          | 889                   |
| ECDSA_P384 | 2545451     | 2544765   | 8164          | 393                   |


These were derived from the following actual data points:

![ED25519 results scatter plot](ed25519.png?raw=true)

![ECDSA_P256 results scatter plot](ecdsa_p256.png?raw=true)

![ECDSA_P384 results scatter plot](ecdsa_p384.png?raw=true)

The red lines in the graph show the mean, the blue ranges show one standard
deviation. The Y axis is scaled to include three standard deviations from
the mean; some outliers beyond this did exist, but were rare as one might 
expect.

Benchmarks of ECDSA_P521 have not yet been completed - this key size is
significantly slower than the others tested above, and is not a key size that
we anticipate using in practice for some time.
