# Named Parameters
Named and Optional parameters for Java 17

### Abstract 

While working on another project, I found a way to make the Java compiler attribute a compilation unit without reporting issues if the AST is not correct.
So I thought about some cool use cases and decided to create this project. 
Both classes and methods are supported.

### How does it work

So let's say that you have a method or class declaration:
```java
void sayHello(String name, String surname){
    System.out.printf("Hello, %s %s%n", name, surname);
}
```

Traditionally, you would call the method like this:
```java
sayHello("Alessandro", "Autiero");
```

Thanks to this plugin, you can use the name of the parameter followed by an assignment operator to specify the parameter:

```java
sayHello(name="Alessandro",surname="Autiero");
```

According to the JLS, assignments can be legally used as arguments. Here are some examples:
```java
var name = "Mario";
sayHello(name="Alessandro",surname="Autiero"); // name is not a named parameter as variable name exists
        
void hello(String name){
  sayHello(name="Alessandro", surname="Autiero"); // name is not a named parameter as method parameter name exists
}

class Whatever {
    private String name;
    
    void hello(){
       sayHello(name="Alessandro", surname="Autiero"); // name is not a named parameter as local variable name exists
    }
}
```
In these cases, "name" will not be treated as a named parameter to preserve backwards compatibility.

If you want to make a parameter optional, you can do so by applying the @Option annotation:
```java
void sayHello(String name, @Option String surname){
    System.out.printf("Hello, %s %s%n", name, surname);
}

sayHello(name="Alessandro");
```

By default, these are the default values passed to the method if the argument isn't specified:

- Object: null
- Number(byte, short, char, int, long, float, double): 0
- Booleans: false
- Arrays: an empty array(for example new int[0])
- Var args(for example int...): not handled to preserve JLS implementation

A specific value can also be provided, both constants and dynamic values are accepted:
```java
// Constant
void sayHello(String name, @Option(18) int age){
    System.out.printf("Hello, %s(age: %s)%n", name, age);
}

// Dynamic values
void sayHello(String name, @Option(getDefaultAge(name)) int age){
    System.out.printf("Hello, %s(age: %s)%n", name, age);
}

int getDefaultAge(String name){
    // Some incredible logic
    return 0;
}

sayHello(name="Alessandro");
```


### How to install
Installing the plugin is pretty easy, all you need to do is add a dependency to your project.

#### Maven
```xml
<dependencies>
    <dependency>
        <groupId>com.github.auties00</groupId>
        <artifactId>named</artifactId>
        <version>1.1</version>
    </dependency>
</dependencies>
```

#### Gradle
```groovy
implementation 'com.github.auties00:named:1.1'
```

#### Gradle Kotlin DSL
```groovy
implementation("com.github.auties00:named:1.1")
```