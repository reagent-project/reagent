# Reagent & Material UI

## Contributing

Please use the demo-template.cljs to create your component demo and it the list in sidebar.cljs file.

## Deployment

Copy this example folder into a new directory outside of this git repo so that it has it own git repo. We don't want to have nested repos. A future improvement would to have a build script that would automatically move it.

This order is important:

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
Then open your browser whatever url it created ie: https://glacial-badlands-20785.herokuapp.com/
