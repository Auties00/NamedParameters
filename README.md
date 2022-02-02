# Named Parameters
Named and Optional parameters for Java 17

### Abstract 

While working on another project, I found a way to make the Java compiler attribute a compilation unit without reporting issues if the AST is not correct.
So I thought about some cool use cases and decided to create this project. Both classes and methods are supported.

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

Considering that, according to the JLS, assignments can be legally used as arguments if an identifier matching the left side of the assignment exists,
an argument is considered named only if said identifier cannot be resolved in the invocation's scope.
This is done to preserve backwards compatibility and, for these reason, such calls are considered simply as positional arguments that contain an assignment. 
In short the parameter "name" in the snippet below is not considered a named parameter in any of the example scenarios.
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

If you want to mark a parameter as Optional, you can do so by applying the @Optional annotation:
```java
void sayHello(String name, @Optional String surname){
    System.out.printf("Hello, %s %s%n", name, surname);
}

sayHello(name="Alessandro");
```

As Java doesn't support non-constant values as annotation parameters(or generic constant values for that matter), 
null or 0 is passed as a parameter depending on whether the type is a primitive or not.

### How to install
Installing the plugin is pretty easy, all you need to do is add a dependency to your project.

#### Maven
```xml
<dependencies>
    <dependency>
        <groupId>com.github.auties00</groupId>
        <artifactId>named</artifactId>
        <version>1.0</version>
    </dependency>
</dependencies>
```

#### Gradle
```groovy
implementation 'com.github.auties00:named:1.0'
```

#### Gradle Kotlin DSL
```groovy
implementation("com.github.auties00:named:1.0")
```