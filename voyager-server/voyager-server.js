const express = require('express');
const { express: voyagerMiddleware } = require('graphql-voyager/middleware');

const app = express();

app.use('/voyager', voyagerMiddleware({ endpointUrl: 'http://localhost:8080/graphql' }));

app.listen(8082, () => {
    console.log('Voyager running at http://localhost:8082/voyager');
});