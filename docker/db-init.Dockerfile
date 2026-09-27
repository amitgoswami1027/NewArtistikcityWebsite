# One-shot job: creates the "artistikcity" database and loads schema + demo data on first run.
FROM mcr.microsoft.com/mssql/server:2022-latest
USER root
COPY database/ /init/database/
COPY docker/db-init.sh /init/db-init.sh
RUN sed -i 's/\r$//' /init/db-init.sh && chmod 755 /init/db-init.sh
USER mssql
ENTRYPOINT ["/bin/bash", "/init/db-init.sh"]
