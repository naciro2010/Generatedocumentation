# Functional Rules Documentation

## Business Rules and Validations

This document lists the functional rules extracted from the codebase.

## Validation

### FR-001: if 'raise ' in stripped and not stripped.startswith('#'):

**Evidence:**

- `generate_docs.py:483`

```python
            if lang == 'python':
                # raise statements
                if 'raise ' in stripped and not stripped.startswith('#'):
                    # Extract error message
                    error_match = re.search(r'raise\s+\w+\(["\']([^"\']+)["\']', stripped)
```


## Assertion

### FR-002: elif 'assert ' in stripped and not stripped.startswith('#'):

**Evidence:**

- `generate_docs.py:509`

```python

                # assert statements
                elif 'assert ' in stripped and not stripped.startswith('#'):
                    start_line = max(0, i - 2)
                    end_line = min(len(lines), i + 3)
```


---
*Auto-generated from code analysis*
