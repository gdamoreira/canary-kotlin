# Improvements manually detected

- Remove all references to local absolute home paths (e.g. `/Users/<name>/.projects/...`). Consider the repository root as `../.projects/`.
- Change all `https://github.com/opentibiabr/canary` references to `https://github.com/gdamoreira/canary-kotlin`
- Hardcoded RSA encryption keys should be moved to a configuration file
- Documentation in `canary/docs` repository should be copied to this repository and updated to reflect the changes made to the codebase.
- README.md should be compatible with original `canary` repository and updated to reflect the changes made to the codebase.
  - should have a section for installation instructions
  - should have a section for contributing instructions
  - should have a section for license information
  - should have a section for troubleshooting instructions
  - should have a section for code of conduct
  - should have a section for security policy
  - should have a section for roadmap
  - should have a section for future improvements
  - should have a section for known issues
  - should have a section for frequently asked questions
  - should have a section for acknowledgments
  - should have a section for architecture explanation
- Github CI/CD pipeline should be set up to automate the test and build.
- `canary` repository contains `data-otserv-global` directory containing files for the game server and should be copied to this repository.
- A full `docker-compose.yml` should be created to automate the deployment of the application.
  - It has been created in the `docker` directory.
  - All necessary data, configuration files, and environment variables should be included.
  - All guides in `canary` repository should be copied to this repository and updated accordingly.

# Rules

- All improvements should be documented in the `docs` directory.
- Each improvement should be committed as separate commits.