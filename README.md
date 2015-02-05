# Tools for Immutables

## Customized version of maven-shade-plugin.

```xml
<groupId>org.immutables.tools</groupId>
<artifactId>maven-shade-plugin</artifactId>
<version>2</version>
```

###1. Relocation with `$` uglyfication
Allows class relocation patterns to contain `$` sign.
Also ending pattern with `$` sign will also prepend
dollar sign to the front of class names.
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

I would love someone to contribute/reimplement those changes to the original Apache Maven maven-shade-plugin.
I just couldn't afford time to dive into processes or infrastructure there.
