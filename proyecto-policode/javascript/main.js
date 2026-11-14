const { main } = require('./src/index');

main().catch((error) => {
  console.error(error.message);
  process.exitCode = 1;
});
