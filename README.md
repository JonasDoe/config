# Config
## Overview
Lean util to prepare config classes by data from config files.
Assume you have a config class extending `config.Config`, looking like this:
```java
@Setting(descriptor = "override", defaultValue = "subValue")
public final String testOverride;
@Setting(isOptional = true)
public List<String> list; // would be a non-final reference to an unmodifiable list
```
Those fields will get set by a config file. Child configs (extending another config) and neseted configs (as fields in another config) are supported as well.

So, config file might look like this:
```
# This "encoding" is actually and optional key word used to determine how to decode this config file
encoding = ISO-8859-1
# this can be used for a nested config
test.nestedId=12345
# will parsed correctly if the config file is encoded in the specified ISO-8859-1
umlaut = asdfäöü
class = java.lang.String
list = 2,3,4
testUrl = http://www.testes.com
withSpacing = has space
```

You can use the config util like this:
```java
File fileToLoad = new File("test.cfg")
// ExampleConfig extends the class config.Config
ExampleConfig config = new ConfigPreparer(fileToLoad).fillConfig(new ExampleChildConfig());

// In case the some fields are not final or modifiable, changes can be stored.
config.store(CONFIG_STORE_FILE);

// You can register custom converter for new data types
config = new ConfigPreparer(fileToLoad).registerConverter(ZoneId, new SettingConverter(Object::toString, ZoneId:of)).update(Config);
```

Check the tests for more examples.

## Installation
Since this project is not hosted at a maven repository, use [jitpack](https://jitpack.io) instead:

Add this repo to your `pom.xml`:
```xml
<repositories>
    <repository>
        <id>jitpack.io</id>
        <url>https://jitpack.io</url>
    </repository>
</repositories>
```
Add this dependency into your `dependencies section` and set the commit has as version:
```xml
<dependency>
    <groupId>com.github.JonasDoe</groupId>
    <artifactId>config</artifactId>
    <version>[commithash]</version>
</dependency>
```
If this does not work, you've got to check this project out and install it with maven (`mvn clean install`).