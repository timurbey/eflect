# `eflect`

`eflect` is an energy accounting system for Java applications. `eflect` uses cross-layer, periodic sampling to produce application-granular energy data. `eflect` currently supports Intel Linux systems.

## Data

`eflect` produces `EnergyFootprint`s of application threads:

```json
{
  "id": 1,
  "name": "Thread-1",
  "energy": 100,
  "start": 0,
  "end": 1000,
  "stack_traces": ["trace1", "trace2"]
}
```
