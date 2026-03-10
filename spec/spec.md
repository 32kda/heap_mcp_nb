* This spec should never be changed by AI
* This project uses NetBeans profiler as a backend
* All operations which could potentially produce more than hundred of elements, like getting all classes or all instances - should have paging using parameters from and to
* Performance-heavy operations results should be cached for possible paging and to avoid to wait for a long time next time such operation will be called 