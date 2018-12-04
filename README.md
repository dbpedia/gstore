# Digital Factory Platform (Server-Side Services) #

This repositories bundles three source code trees for services for the Digital
Factory Platform:

* a Scalatra-based REST-service for uploading and managing DataIds to appear in
 the DFP repo (the `scala` subdirectory)
* an internal daemon service that inspects a directory for files to be loaded 
 into a Virtuoso Open Source instance (the `scala/vos-load` subdirectory)
* a deprecated early PHP project for uploading DataIds (the `upload` folder)

Please refer for details to the `README.md` files in the corresponding 
subdirectories.
