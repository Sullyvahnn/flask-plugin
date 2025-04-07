# Python Types Plugin 24.3.5 - PyCharm Professional

## Overview
Python Types Plugin 24.3.5 is a powerful tool designed for PyCharm Professional, built using IntelliJ. It enhances variable analysis by providing two key functionalities:
1. **Variable Type Widget** - Displays all possible types of a selected variable.
2. **Tree-Like Structure Visualization** - Shows the hierarchical relationships of variable assignments and their potential types.

## Features
### 1. Variable Type Widget
When selecting a variable in your code, a widget appears in the PyCharm status bar listing all possible types that the variable can take. This feature helps in understanding the dynamic typing of variables in Python.

#### How It Works:
- Select a variable in your code.
- A widget appears in the status bar displaying every possible type the variable can have.
- Clicking on a specific type highlights the line where the variable is assigned.

### 2. Tree-Like Structure Visualization
This feature visualizes variable relationships in a hierarchical format, making it easier to track dependencies and type propagation.

#### Example:
For the following code:
```python
x = y
y = 2
y = 2.0
```
The tree structure would display:
```
x → y
y → int, float
```
This provides an intuitive way to track variable assignments and type evolution throughout the code.

## Installation
1. Its demo version. It cannot be installed
2. Clone git repository and run gradle build plugin

## Usage
- Simply click on a variable to activate the widgets.
- View possible types in the status bar.
- Click on a type to navigate to its assignment.
- Expand the tree view to explore dependencies and types.

## Compatibility
- **IDE**: PyCharm Professional
- **Version**: 24.3.5
- **Framework**: Flask
- **Language Support**: Python 3.6+


