import sys
import time
import inspect
import functools
from typing import Dict, List, Callable, Set, Any, Tuple, Optional
from dataclasses import dataclass
from collections import defaultdict


@dataclass
class FunctionStats:
    """Class for storing statistics about a traced function."""
    call_count: int = 0
    total_time: float = 0.0
    min_time: float = float('inf')
    max_time: float = 0.0

    @property
    def avg_time(self) -> float:
        """Calculate average execution time."""
        return self.total_time / self.call_count if self.call_count > 0 else 0


class FunctionTracer:
    _instance = None

    @classmethod
    def get_instance(cls):
        """Get or create a singleton instance of FunctionTracer."""
        if cls._instance is None:
            cls._instance = FunctionTracer()
        return cls._instance

    @classmethod
    def trace(cls, func=None):
        """
        Decorator to mark a function for tracing.

        Can be used as:
            @FunctionTracer.trace
            def function_to_trace():
                pass

        Returns:
            The original function unchanged
        """
        def decorator(function):
            tracer = cls.get_instance()
            # Register this function with the tracer
            if not hasattr(tracer, '_decorated_functions'):
                tracer._decorated_functions = set()
            tracer._decorated_functions.add(function)

            @functools.wraps(function)
            def wrapper(*args, **kwargs):
                return function(*args, **kwargs)

            return wrapper

        if func is None:
            return decorator
        return decorator(func)

    def __init__(self):
        self._enabled = False
        self._traced_functions: Set[Callable] = set()
        self._decorated_functions: Set[Callable] = set()
        self._stats: Dict[Callable, FunctionStats] = defaultdict(FunctionStats)
        self._call_stack: Dict[int, Tuple[Callable, float]] = {}
        self._original_trace_function = None
        self._enabled = False
        self._original_trace_function = None

        self._builtin_functions: Set[Callable] = set()
        self._original_builtins: Dict[Callable, Callable] = {}
        self._wrapped_builtins: Dict[Callable, Callable] = {}

    def enable(self, functions: List[Callable] = None) -> None:
        """
        Enable function tracing for the specified functions.

        Args:
            functions: List of function objects to trace
        """
        print("[FunctionTracer] Enabling tracing...")

        if self._enabled:
            # Already enabled; update the function list if needed
            if functions:
                print(f"[FunctionTracer] Already enabled, updating functions: {functions}")
                self.update_functions(functions)
            return

        self._traced_functions = set(self._decorated_functions)
        print(f"[FunctionTracer] Decorator-traced functions: {self._decorated_functions}")

        if functions:
            for func in functions:
                if self._is_builtin_function(func):
                    self._builtin_functions.add(func)
                else:
                    self._traced_functions.add(func)

            func_names = [f.__name__ if hasattr(f, '__name__') else str(f) for f in functions]
            print(f"[FunctionTracer] Adding functions to trace: {func_names}")

        import sys
        current_tracer = sys.gettrace()
        self._original_trace_function = current_tracer
        print(f"[FunctionTracer] Captured original tracer: {current_tracer}")

        sys.settrace(self._trace_function)

        self._setup_builtin_tracing()

        self._enabled = True
        print("[FunctionTracer] Tracing enabled successfully")

    def disable(self) -> Dict[Callable, FunctionStats]:
        """
        Disable function tracing and return the collected statistics.

        Returns:
            Dictionary mapping functions to their execution statistics
        """
        if not self._enabled:
            return dict(self._stats)

        sys.settrace(self._original_trace_function)

        self._restore_builtin_functions()

        self._enabled = False
        self._call_stack.clear()

        return dict(self._stats)

    def update_functions(self, functions: List[Callable]) -> None:
        """
        Update the list of functions being traced without disabling tracing.

        Args:
            functions: New list of function objects to trace
        """
        if not self._enabled:
            raise RuntimeError("Tracing is not enabled")

        self._restore_builtin_functions()

        self._traced_functions = set(self._decorated_functions)  # Keep decorated functions
        self._builtin_functions.clear()

        for func in functions:
            if self._is_builtin_function(func):
                self._builtin_functions.add(func)
            else:
                self._traced_functions.add(func)

        self._setup_builtin_tracing()

    def get_results(self) -> Dict[Callable, FunctionStats]:
        """
        Get the current results of function tracing.

        Returns:
            Dictionary mapping functions to their execution statistics
        """
        return dict(self._stats)

    def _is_builtin_function(self, func: Callable) -> bool:
        """
        Check if a function is a built-in function that needs special handling.

        Args:
            func: Function to check

        Returns:
            True if the function is a built-in function, False otherwise
        """
        return (not hasattr(func, '__code__') and callable(func))

    def _setup_builtin_tracing(self) -> None:
        """Set up tracing for built-in functions by wrapping them."""
        for func in self._builtin_functions:
            if func in self._wrapped_builtins:
                continue  # Already wrapped

            self._original_builtins[func] = func

            @functools.wraps(func)
            def wrapped_function(*args, **kwargs):
                if not self._enabled:
                    return func(*args, **kwargs)

                start_time = time.perf_counter()
                result = func(*args, **kwargs)
                duration = time.perf_counter() - start_time

                stats = self._stats[func]
                stats.call_count += 1
                stats.total_time += duration
                stats.min_time = min(stats.min_time, duration)
                stats.max_time = max(stats.max_time, duration)

                return result

            self._wrapped_builtins[func] = wrapped_function

            if hasattr(func, '__module__') and hasattr(func, '__name__'):
                try:
                    module_name = func.__module__
                    func_name = func.__name__

                    import importlib
                    module = importlib.import_module(module_name)
                    setattr(module, func_name, wrapped_function)
                except Exception as e:
                    print(f"Warning: Could not wrap {func.__name__}: {e}")

    def _restore_builtin_functions(self) -> None:
        """Restore original built-in functions."""
        for func, original in self._original_builtins.items():
            if hasattr(func, '__module__') and hasattr(func, '__name__'):
                try:
                    module_name = func.__module__
                    func_name = func.__name__

                    import importlib
                    module = importlib.import_module(module_name)
                    setattr(module, func_name, original)
                except Exception as e:
                    print(f"Warning: Could not restore {func.__name__}: {e}")

        self._builtin_functions.clear()
        self._original_builtins.clear()
        self._wrapped_builtins.clear()

    def _trace_function(self, frame, event, arg) -> Optional[Callable]:
        """
        Trace function that will be registered with sys.settrace().
        We need to carefully chain to PyCharm's trace function so both can work.

        Returns:
            Another tracer function if we want deeper tracing, otherwise None.
        """
        if not self._enabled:
            return self._original_trace_function(frame, event, arg) if self._original_trace_function else None

        func = None
        try:
            if event == 'call':
                code = frame.f_code
                func_name = code.co_name

                for traced_func in self._traced_functions:
                    if hasattr(traced_func, '__code__') and traced_func.__code__ is code:
                        func = traced_func
                        print(f"[FunctionTracer] Tracing function call: {func_name}")
                        break

                if func is not None:
                    thread_id = id(frame)
                    self._call_stack[thread_id] = (func, time.perf_counter())

            elif event == 'return':
                thread_id = id(frame)
                if thread_id in self._call_stack:
                    func, start_time = self._call_stack.pop(thread_id)
                    duration = time.perf_counter() - start_time
                    func_name = func.__name__ if hasattr(func, '__name__') else str(func)

                    stats = self._stats[func]
                    stats.call_count += 1
                    stats.total_time += duration
                    stats.min_time = min(stats.min_time, duration)
                    stats.max_time = max(stats.max_time, duration)

                    print(f"[FunctionTracer] Function {func_name} completed in {duration:.6f}s")

        except Exception as e:
            print(f"[FunctionTracer] Tracing error: {e}")

        pycharm_next = None
        if self._original_trace_function:
            try:
                pycharm_next = self._original_trace_function(frame, event, arg)
            except Exception as e:
                print(f"[FunctionTracer] Error in PyCharm tracer: {e}")

        if func is not None:
            return self._trace_function
        else:
            return pycharm_next


    def format_results(self) -> str:
        """
        Format the tracing results into a human-readable string.

        Returns:
            Formatted results as a string
        """
        if not self._stats:
            return "No tracing data collected."

        lines = ["Function Tracing Results:", "-" * 80]
        header = f"{'Function Name':<40} {'Calls':<10} {'Total (s)':<12} {'Avg (ms)':<12} {'Min (ms)':<12} {'Max (ms)':<12}"
        lines.append(header)
        lines.append("-" * 80)

        for func, stats in self._stats.items():
            if callable(func):
                if hasattr(func, '__name__'):
                    if hasattr(func, '__module__') and func.__module__ != '__main__':
                        func_name = f"{func.__module__}.{func.__name__}"
                    else:
                        func_name = func.__name__
                else:
                    func_name = str(func)
            else:
                func_name = str(func)

            if len(func_name) > 38:
                func_name = "..." + func_name[-35:]

            line = (
                f"{func_name:<40} "
                f"{stats.call_count:<10} "
                f"{stats.total_time:<12.6f} "
                f"{stats.avg_time * 1000:<12.6f} "
                f"{stats.min_time * 1000:<12.6f} "
                f"{stats.max_time * 1000:<12.6f}"
            )
            lines.append(line)

        return "\n".join(lines)
