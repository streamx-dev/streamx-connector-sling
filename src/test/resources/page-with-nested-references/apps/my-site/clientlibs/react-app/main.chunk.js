console.log("App initialized with assets");
fetch("/etc.clientlibs/my-site/assets/config.json")
  .then(response => response.json());
