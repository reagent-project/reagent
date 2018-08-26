# Reagent & Material UI

## Contributing

Please use the demo-template.cljs to create your component demo and it the list in sidebar.cljs file.

## Deployment

Copy this example folder into a new directory outside of this git repo so that it has it own git repo. We don't want to have nested repos. A future improvement would to have a build script that would automatically move it.

!! Copy and paste this folder into a new directory outside of this project repo !!

We don't want to cross repos here.

1. View the project the browser which also compiles necessary files that are later built in the uberjar

```
lein figwheel
```

2. Create the uberjar

```
lein with-profile -dev,+production uberjar
```

3. Deploy to Heroku

```
heroku login
git init
git add .
git commit -m "first commit"
heroku create
git push heroku master
heroku ps:scale web=1
heroku open
```

Note: because you moved this into a new directory, you shouldn't have a problem but if you happen to have github repo too you will need to add heroku. After you do the step `heroko create` you will need to add the heroku path `heroku git:remote -a radiant-falls-68370` replacing that radiant-falls-68370 with whatever heroku spit out for you.


Then open your browser whatever url it created ie: https://glacial-badlands-20785.herokuapp.com/
