A note about these models:

Though they are similar to the models in vinyldns.core, they exist to utilize the uPickle library for json reading
and writing.

To utilize uPickle, attribute types have to be something uPickle knows how to read and write. These can be Scala
built-ins, or other custom models in this directory, but things like joda data time won't work as there is no Scala js
version of joda date time. Additionally, attribute names have to match how the API reads and writes json exactly.
