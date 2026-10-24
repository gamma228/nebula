# Code Review Workflow (/review)
Perform static analysis and architecture reviews:
- Verify Clean Architecture and MVVM/MVI boundaries
- Ensure sealed Result usage, no silent catch blocks, no mutable singleton leaks
- Check Kotlin idioms, coroutine cancellation handling, and Compose recomposition stability
