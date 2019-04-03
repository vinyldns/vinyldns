A note about these models:

Though they are similar to the models in vinyldns.core, they exist to utilize the uPickle library for json reading
and writing. Attribute names of classes have to match how the API reads and writes json exactly.

To utilize uPickle, attribute types have to be something uPickle knows how to read and write. These can be Scala
built-ins, or other custom models in this directory.

Would have loved to somehow incorporate models in vinyldns.core, but certain types in there are not compatible
with Scala.js, such as Joda Data Time. You can still depend on core if you want, but just make sure you do not try
to use classes that utilize non Scala.js compatible types, or else there will be linking errors

Other than that, uPickle is pretty nifty, makes it very easy to read and write and json, and many examples can
be seen throughout the codebase

Note: When the model contains options you MUST extend OptionRW on the companion object
