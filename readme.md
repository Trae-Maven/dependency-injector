# Dependency Injector

A lightweight and flexible dependency injection framework for Java.

Dependency Injector provides a simple container for managing object creation and wiring dependencies automatically using constructor injection, field injection, and configuration binding.

The framework is designed to be lightweight, fast, and easy to integrate into existing Java applications.

## Features

- Constructor injection
- Field injection
- Configuration injection
- Lightweight dependency container
- Minimal dependencies
- Designed for modern Java (Java 21+)
- Easy integration into existing projects

## Built-in Dependencies

Dependency Injector includes several internal helper utilities used throughout the framework.

- [Utilities](https://github.com/Trae-Maven/utilities) – Shared helper classes and performance-focused utilities used internally by the framework.

These dependencies are included automatically through Maven and do not need to be installed separately.

## Installation

Add the dependency to your Maven project:

```xml
<dependency>
    <groupId>io.github.trae</groupId>
    <artifactId>dependency-injector</artifactId>
    <version>0.0.1</version>
</dependency>
```
