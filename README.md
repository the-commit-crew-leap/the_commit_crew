# The Commit Crew

## Branching strategy: Git flow
We decided to go with git flow branching strategy because we expect to have many features, scheduled releases, and it is safer than pushing straight to main. 


## Group Members:
- Giulia Fattori
- Myroslava Bunciuc
- Ian Gill
- Louise Deeth

## Running the application:

1. Clone the repository:
Windows:
```bash
git clone https://github.com/poppybun/the_commit_crew.git
```
Linux:
```bash
git clone git@github.com:poppybun/the_commit_crew.git
```
2. The databases (the-commit-crew-prod & the-commit-crew-dev) can be connected to through pgAdmin.
Steps 3-5 outline how to run a copy of either database locally and can be skipped.

3. To run copies of the databases locally, create .env.dev and .env.prod files in the root directory of the project.
They should look something like this:

.env.dev:
```bash
POSTGRES_DB=the-commit-crew-dev
POSTGRES_PASSWORD=<db password here>
```

.env.prod:
```bash
POSTGRES_DB=the-commit-crew-prod
POSTGRES_PASSWORD=<db password here>
```  

4. Run docker-compose:<br />
dev:
```bash 
docker-compose -p commitcrew-dev  --env-file .env.dev  up -d --build
```
prod:
```bash 
docker-compose -p commitcrew-prod --env-file .env.prod up -d --build
```

5. Verify: <br />
dev:
```bash
docker-compose -p commitcrew-dev  --env-file .env.dev  exec db psql -U postgres -d the-commit-crew-dev  -c "\dt"
```

prod:
```bash
docker-compose -p commitcrew-prod --env-file .env.prod exec db psql -U postgres -d the-commit-crew-prod -c "\dt"
```
You should see the list of tables for each database.
```

## Coding Conventions

### Naming 
- Classes: PascalCase (e.g. `UserService`)
- Methods/variables: camelCase (e.g. `isActive`)
- Constants: UPPER_SNAKE_CASE (e.g. `MAX_RETRY_ATTEMPTS`)
- Tables: lower_snake_case (e.g. `accounts_example`)

### Code Style
- Indent: 1 tab
- Line length: max 120 characters
- One class per file

### Git Workflow & Branch Protection
- **Develop branch**: All pushes require a pull request with 1 team member approval
- **Main branch**: Merges require 2 team member approvals before integration
- Follow Git flow: features branch from `develop`, releases branch from `develop`, hotfixes branch from `main`


## Jenkins Pipeline available at
[10.9.75.153:8080](http://10.9.75.153:8080) (develop & feature/)<br />
[10.9.70.90:8080](http://10.9.70.90:8080/job/the-commit-crew-main/) (main)

## Jira backlog:

[The Commit Crew](https://thecommitcrew.atlassian.net/jira/software/projects/SCRUM/boards/1/backlog)