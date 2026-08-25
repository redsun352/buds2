# Startup crash fix

The BudsManagerActivity startup crash reported as:

`IllegalStateException: The specified child already has a parent`

was caused by adding `rawStatus` to the Activity root and then adding the same View to `logScroll`.

The current implementation gives `rawStatus` exactly one parent (`logScroll`).

Reference commit: `b279ef5e55427121be44d3688a6a2f3b53a48674`
