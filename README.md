# Tools for Immutables

## Customized version of maven-shade-plugin.

```xml
<groupId>org.immutables.tools</groupId>
<artifactId>maven-shade-plugin</artifactId>
<version>3</version>
```

###1. Relocation with `$` uglyfication
Allows class relocation patterns to contain `$` sign.
If pattern ends with `$` sign, the dollar sign will be prepended in front of class names.
This effectively prevents clashed during code completion.

```xml
<relocation>
  <pattern>com.google.common</pattern>
  <shadedPattern>com.my.internal.$guava$</shadedPattern>
</relocation>
```

The above pattern will result in `ImmutableList` being relocated as

```
com.my.internal.$guava$.collect.$ImmutableList
```

###2. ServiceResourceTransformer that actually works properly

* Merges `META-INF/services/*` entries with deduplication of lines
* Applied class relocations to merged entries

###3. Minimize Jar that works properly
* Do not removes used classes: fixed transitive class dependencies problem.
* Do not remove classes used in `META-INF/services/*` for used service types. Removes `META-INF/services/*` for unused service types. Explicitly include `META-INF/services/**` in filters to not delete such files and make all provider implementation types marked as non-removable.

I would love someone to contribute/reimplement those changes to the original Apache Maven maven-shade-plugin.
I just couldn't afford time to dive into processes or infrastructure there.
