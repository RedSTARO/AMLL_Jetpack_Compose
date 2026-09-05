# Third-party notices

## Apple Music-like Lyrics

This library is a Kotlin Multiplatform / Compose Multiplatform adaptation of:

- `@applemusic-like-lyrics/core` version `0.5.2`
- `@applemusic-like-lyrics/lyric` version `1.0.2`
- `@applemusic-like-lyrics/ttml` version `1.0.1`

Upstream project: [amll-dev/applemusic-like-lyrics](https://github.com/amll-dev/applemusic-like-lyrics)

Copyright (C) the Apple Music-like Lyrics contributors.

Immutable upstream preferred-source revisions are
[`fd7ec2d`](https://github.com/amll-dev/applemusic-like-lyrics/tree/fd7ec2d597daa2a66a37ca5f3214d6757ec17cfa)
for Core 0.5.2 / Lyric 1.0.2 and
[`36e5703`](https://github.com/amll-dev/applemusic-like-lyrics/tree/36e57035a735596479abe943f78846b8d1e78afc)
for TTML 1.0.1.

The Kotlin lyric parser, optimizer, timeline, layout, spring, interlude,
word-mask, emphasis, reduced-motion behavior and artwork background in this
repository are a platform adaptation of that work, distributed under the GNU
Affero General Public License, version 3 only (`AGPL-3.0-only`). The
translation was produced for RedefineNCM KMP on 2026-07-27 and extracted into
this standalone repository on 2026-09-06.

The symbol-level source-to-Kotlin mapping is documented in
[`docs/AMLL_NATIVE_TRANSLATION.md`](docs/AMLL_NATIVE_TRANSLATION.md).

The bundled full license text is
[`LICENSES/AGPL-3.0-only.txt`](LICENSES/AGPL-3.0-only.txt) (also published as
[`LICENSE`](LICENSE)); the canonical upstream text is available at
<https://www.gnu.org/licenses/agpl-3.0.txt>.

## NetEase YRC dialect

`LyricParser.parseYrc` follows the YRC dialect the RedefineNCM AMLL host
consumed, not `@applemusic-like-lyrics/lyric`'s generic `formats/yrc.ts`. It is
covered by the same `AGPL-3.0-only` terms as the rest of this repository.

## Runtime dependencies

This library declares Compose Multiplatform, Coil 3, kotlinx-coroutines,
kotlinx-serialization, xmlutil and JNA. Their licenses are those published by
their respective upstream projects; none of them is vendored here.
