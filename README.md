# Circe and Cats for Dotty

[![Build status](https://img.shields.io/travis/travisbrown/dotty-experiments/master.svg)](https://travis-ci.org/travisbrown/dotty-experiments)

This is an experimental project that ports [Circe][circe] and [Cats][cats] from Scala 2 to Dotty,
a language and compiler that will become Scala 3.

## Motivation

The goal of this experiment isn't primarily to allow the use of Circe or Cats on Dotty:
the Dotty compiler provides compatibility with Scala 2 artifacts that makes this largely
unnecessary, and apart from some macro-supported functionality such as Circe's generic derivation
or Cats's `FunctionK` syntax, using Cats and Circe from Dotty is just a matter of sticking
`withDottyCompat` on your dependencies (see the `compat` directory for a configured example
project). Instead this project is intended to be a kind of sandbox for trying out new language
features in the context of these libraries.

## Changes

This project is an experiment and its shape and scope are subject to change. Right now there are a
few major differences between its implementation and the original Scala 2 libraries:

* Cats's standard library instances are not orphaned. One of the criticisms of Scala 2 that Dotty
  explicitly aims to address is "over-reliance on implicit imports". I've always hated the fact
  that Cats requires imports for standard library instances, and I've always suspected that the
  difficulty of unorphaning them was exaggerated, but making the change was even easier than I
  expected. I don't _think_ it relies on anything Dotty-specific, and I'll probably take a shot at
  backporting it to Scala 2 as a proposal for Cats 3 (although I'm not holding my breath).
* I've changed `NonEmptyChain` to use an opaque type-based representation.
* Type class derivation is supported via `derives` for a few type classes, including `Eq`,
  `Monoid`, `Encoder`, and `Decoder`.
* I've implemented a simplified version of the circe-literal `json` string interpolator using
  Dotty macros (interpolating variables into key positions isn't supported, for example).
* The top-level `cats` package has been moved into the `io.circe` namespace (I did this because I
  originally thought I might want to try things out side-by-side with the official Scala 2 Cats
  packages).
* The tests that have been moved into the Dotty ports (which isn't most of them) have also been
  migrated from ScalaTest to [Minitest][minitest]. While it may be possible to use ScalaTest
  from Dotty, the pervasive use of macros makes it uncomfortable, and I was running into enough
  problems that I just decided to switch, and have had essentially zero issues with the Scala 2
  Minitest artifacts. For what it's worth I'm considering backporting the Minitest migration of
  the Circe tests from here to Circe proper.

## Future

I'm likely to keep playing with this, and will probably publish artifacts at some point.

## Licenses

### Circe

Circe is licensed under the **[Apache License, Version 2.0][apache]** (the
"License"); you may not use this software except in compliance with the License.

### Cats

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.

All code is available to you under the MIT license, available at http://opensource.org/licenses/mit-license.php and also in the COPYING file. The design is informed by many other projects, in particular Scalaz.

Copyright the maintainers, 2015-2019.

[apache]: http://www.apache.org/licenses/LICENSE-2.0
[cats]: https://github.com/typelevel/cats
[circe]: http://circe.io/
[dotty]: https://dotty.epfl.ch
[dotty-motivation]: https://dotty.epfl.ch/docs/reference/contextual/motivation.html
[minitest]: https://github.com/monix/minitest
